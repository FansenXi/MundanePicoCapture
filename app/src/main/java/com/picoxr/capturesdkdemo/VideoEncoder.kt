package com.picoxr.capturesdkdemo

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

class VideoEncoder {
    private val TAG = "VideoEncoder"
    
    private var mediaCodec: MediaCodec? = null
    private var encoderCallback: EncoderCallback? = null
    private var encodeThread: Thread? = null
    private var isEncoding = false
    private val frameQueue = ArrayBlockingQueue<FrameData>(30) // 30帧缓冲
    private var outputStream: OutputStream? = null
    private var socket: Socket? = null
    
    interface EncoderCallback {
        fun onEncodedData(data: ByteBuffer, info: MediaCodec.BufferInfo)
        fun onEncoderError(error: Exception)
        fun onEncoderStarted()
        fun onEncoderStopped()
    }
    
    private data class FrameData(
        val surface: Surface,
        val presentationTimeUs: Long
    )
    
    fun setCallback(callback: EncoderCallback) {
        this.encoderCallback = callback
    }
    
    @Throws(IOException::class)
    fun prepare(width: Int, height: Int, frameRate: Int, bitRate: Int) {
        // 创建HEVC编码器
        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        
        // 配置编码器参数
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 每秒一个关键帧
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
        
        // 创建并配置编码器
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        mediaCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        Log.i(TAG, "HEVC encoder prepared: $width x $height, $frameRate fps, $bitRate bps")
    }
    
    @Throws(IOException::class)
    fun connectToServer(serverIp: String, serverPort: Int) {
        // 先关闭现有连接
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing existing connection: ${e.message}")
        }
        
        // 创建新连接
        socket = Socket(serverIp, serverPort)
        outputStream = socket?.getOutputStream()
        Log.i(TAG, "Connected to server: $serverIp:$serverPort")
    }
    
    fun startEncoding() {
        if (isEncoding || mediaCodec == null) {
            return
        }
        
        isEncoding = true
        encodeThread = Thread { encodeLoop() }
        encodeThread?.start()
        encoderCallback?.onEncoderStarted()
        Log.i(TAG, "HEVC encoding started")
    }
    
    fun stopEncoding() {
        isEncoding = false
        encodeThread?.join(1000)
        encodeThread = null
        
        // 清空队列
        frameQueue.clear()
        
        encoderCallback?.onEncoderStopped()
        Log.i(TAG, "HEVC encoding stopped")
    }
    
    fun queueFrame(surface: Surface, presentationTimeUs: Long) {
        if (isEncoding) {
            frameQueue.offer(FrameData(surface, presentationTimeUs))
        }
    }
    
    private fun encodeLoop() {
        mediaCodec?.let { codec ->
            try {
                // 启动编码器
                codec.start()
                
                val bufferInfo = MediaCodec.BufferInfo()
                
                while (isEncoding) {
                    try {
                        // 获取帧数据
                        val frameData = frameQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                        if (frameData != null) {
                            // 将Surface中的数据提交给编码器
                            val inputBufferId = codec.dequeueInputBuffer(1000)
                            if (inputBufferId >= 0) {
                                // 对于Surface输入，不需要处理inputBuffer，直接渲染到encoder的input surface
                                codec.queueInputBuffer(inputBufferId, 0, 0, frameData.presentationTimeUs, 0)
                            }
                        }
                        
                        // 处理编码输出
                        processEncodedOutput(codec, bufferInfo)
                        
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in encode loop: ${e.message}")
                        encoderCallback?.onEncoderError(e)
                    }
                }
                
                // 处理剩余的编码数据
                codec.signalEndOfInputStream()
                val finalBufferInfo = MediaCodec.BufferInfo()
                while (true) {
                    val outputBufferId = codec.dequeueOutputBuffer(finalBufferInfo, 1000)
                    if (outputBufferId < 0) {
                        break
                    }
                    processEncodedOutput(codec, finalBufferInfo)
                    codec.releaseOutputBuffer(outputBufferId, false)
                    if (finalBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
                
                // 停止编码器
                codec.stop()
                
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in encode loop: ${e.message}")
                encoderCallback?.onEncoderError(e)
            }
        }
    }
    
    private fun processEncodedOutput(codec: MediaCodec, bufferInfo: MediaCodec.BufferInfo) {
        var outputDone = false
        
        while (!outputDone) {
            // 获取可用的输出缓冲区
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 100)
            
            when {
                outputBufferId >= 0 -> {
                    // 获取输出缓冲区数据
                    val outputBuffer = codec.getOutputBuffer(outputBufferId)
                    outputBuffer?.let { buffer ->
                        if (bufferInfo.size > 0) {
                            // 处理编码后的数据
                            encoderCallback?.onEncodedData(buffer, bufferInfo)
                            
                            // 发送数据到服务器
                            outputStream?.let { os ->
                                try {
                                    // 发送数据大小
                                    val sizeBytes = ByteBuffer.allocate(4).putInt(bufferInfo.size).array()
                                    os.write(sizeBytes)
                                    
                                    // 发送数据内容
                                    val data = ByteArray(bufferInfo.size)
                                    buffer.position(bufferInfo.offset)
                                    buffer.get(data)
                                    os.write(data)
                                    os.flush()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Error sending data: ${e.message}")
                                    encoderCallback?.onEncoderError(e)
                                }
                            }
                        }
                    }
                    
                    // 释放输出缓冲区
                    codec.releaseOutputBuffer(outputBufferId, false)
                    
                    // 检查是否结束
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 输出格式变化
                    val newFormat = codec.outputFormat
                    Log.i(TAG, "Output format changed: $newFormat")
                    
                    // 发送格式信息到服务器
                    outputStream?.let { os ->
                        try {
                            val formatBytes = "HEVC".toByteArray(Charsets.UTF_8)
                            os.write(formatBytes)
                            os.flush()
                        } catch (e: IOException) {
                            Log.e(TAG, "Error sending format info: ${e.message}")
                            encoderCallback?.onEncoderError(e)
                        }
                    }
                }
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // 没有可用的输出缓冲区，稍后重试
                    outputDone = true
                }
            }
        }
    }
    
    fun release() {
        stopEncoding()
        
        try {
            mediaCodec?.release()
            mediaCodec = null
            
            outputStream?.close()
            outputStream = null
            
            socket?.close()
            socket = null
            
            Log.i(TAG, "HEVC encoder released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing encoder: ${e.message}")
        }
    }
    
    // 获取编码器的输入Surface，用于接收相机数据
    fun getInputSurface(): Surface? {
        return mediaCodec?.createInputSurface()
    }
}