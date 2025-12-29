package com.picoxr.capturesdkdemo

import android.Manifest
import android.media.ImageReader
import android.media.MediaCodec
import android.os.Bundle
import android.os.Environment
import android.provider.Settings.Global
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.picoxr.capturesdkdemo.permissions.PermissionUtils
import com.pxr.capturelib.PXRCapture
import com.pxr.capturelib.PXRCaptureCallBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var imageReader: ImageReader

    private var pxrCapture: PXRCapture? = null
    private var videoEncoder: VideoEncoder? = null
    private var isEncodingEnabled: Boolean = false

    private lateinit var btnCapture: Button
    private lateinit var btnStartRecord: Button
    private lateinit var btnStopRecord: Button
    private lateinit var btnStartPreview: Button
    private lateinit var btnStopPreview: Button
    private lateinit var btnGetCameraParam:Button
    private lateinit var btnStartEncode: Button
    private lateinit var btnStopEncode: Button
    private lateinit var tvCameraParam:TextView
    private lateinit var checkBox:CheckBox
    private lateinit var surfaceView: SurfaceView

    private var captureState:Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(TAG, "onCreate")
        surfaceView = findViewById(R.id.surfaceView)
        btnCapture = findViewById(R.id.btn_capture)
        btnStartPreview = findViewById(R.id.btn_start_preview)
        btnStopPreview = findViewById(R.id.btn_stop_preview)
        btnStartRecord = findViewById(R.id.btn_start_record)
        btnStopRecord = findViewById(R.id.btn_stop_record)
        btnGetCameraParam = findViewById(R.id.btn_get_camera_param)
        btnStartEncode = findViewById(R.id.btn_start_encode)
        btnStopEncode = findViewById(R.id.btn_stop_encode)
        tvCameraParam = findViewById(R.id.tv_camera_param)
        checkBox = findViewById(R.id.check_box)

        btnCapture.setOnClickListener { takePicture() }
        btnStartPreview.setOnClickListener { startPreview() }
        btnStopPreview.setOnClickListener { stopPreview() }
        btnStartRecord.setOnClickListener { startRecord() }
        btnStopRecord.setOnClickListener { stopRecord() }
        btnGetCameraParam.setOnClickListener { getCameraParams() }
        btnStartEncode.setOnClickListener { startEncode() }
        btnStopEncode.setOnClickListener { stopEncode() }
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            GlobalScope.launch(Dispatchers.Default) {
                releaseCamera()
                initPXRCapture(isChecked)
            }
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback2 {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.i(TAG, "surfaceCreated")
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.i(TAG, "surfaceChanged $format $width $height")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.i(TAG, "surfaceDestroyed")
            }

            override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
                Log.i(TAG, "surfaceRedrawNeeded")
            }

        })

        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        val checkCode: Int = PermissionUtils.checkPermission(this, permissions, true)
        if (checkCode == PermissionUtils.CHECK_RESULT_OK) {
            Log.i(TAG, "checkPermission OK")
            initPXRCapture(false)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        initPXRCapture(false)
    }

    private fun initPXRCapture(check:Boolean){
        Log.i(TAG, "initPXRCapture")
        pxrCapture = PXRCapture()
        val cameraParams= mutableMapOf<String, String>(
            PXRCapture.CAMERA_PARAMS_KEY_MCTF to PXRCapture.VALUE_TRUE,
            PXRCapture.CAMERA_PARAMS_KEY_EIS to PXRCapture.VALUE_TRUE,
            PXRCapture.CAMERA_PARAMS_KEY_MFNR to PXRCapture.VALUE_TRUE
        )
        pxrCapture?.openCameraAsync(cameraParams)
        pxrCapture?.setCallback(object :PXRCaptureCallBack{
            override fun captureOnStateChanged(state: Int) {

                Log.i(TAG, "captureOnStateChanged state: $state")
                captureState = state
                if (captureState == PXRCapture.PXRCaptureState.CAPTURE_STATE_CAMERA_OPENED.ordinal){
                    captureConfig(check)
                }
            }

            override fun captureOnError(error: Int) {
                Log.i(TAG, "captureOnError state: $error")
            }

            override fun captureOnEnviromentInfoChanged(lut: Int, distance: Int) {

            }

            override fun captureOnTakePictureComplete(
                disparityScale:Float, disparityShift:Float, hfov:Float, vFov:Float, cameraIpd:Float
            ) {
                Log.i(
                    TAG,
                    "captureOnTakePictureComplete disparityScale: $disparityScale disparityShift: $disparityShift"
                )
            }

            override fun captureOnTakePictureTimeOut() {
                Log.i(TAG, "captureOnTakePictureTimeOut")
            }

        })

    }

    private fun releaseCamera(){        
        pxrCapture?.run {
            stopPreview()
            stopRecord()
            reset()
            closeCamera()
            release()
        }
        pxrCapture = null
        
        // 释放编码器资源
        videoEncoder?.release()
        videoEncoder = null
        isEncodingEnabled = false
    }

    private fun captureConfig(check: Boolean){
        Log.i(TAG, "captureConfig: $check")
        val configureParams = mutableMapOf(
            PXRCapture.CONFIGURE_PARAMS_KEY_ENABLE_MVHEVC to PXRCapture.VALUE_FALSE,
            PXRCapture.CONFIGURE_PARAMS_KEY_VIDEO_FPS to  "60",
            PXRCapture.CONFIGURE_PARAMS_KEY_VIDEO_WIDTH to "2048",
            PXRCapture.CONFIGURE_PARAMS_KEY_VIDEO_HEIGHT to "${1536/2}",
            PXRCapture.CONFIGURE_PARAMS_KEY_VIDEO_BITRATE to "${10 * 1024 * 1024}",
        )
        if (!check){
            configureParams.put("output-camera-raw-data", PXRCapture.VALUE_TRUE)
        }
        pxrCapture?.configure(configureParams)
    }

    private fun getCameraParams() {
        val cameraIntrinsics = DoubleArray(4)
        val len = IntArray(1)
        pxrCapture?.getCameraIntrinsics(
            VIDEO_WIDTH,
            VIDEO_HEIGHT,
            VIDEO_H_FOV,
            VIDEO_V_FOV,
            cameraIntrinsics,
            len
        )

        val leftCameraExtrinsics = DoubleArray(16)
        val leftCameraExtrinsicsLen = IntArray(1)
        val rightCameraExtrinsics = DoubleArray(16)
        val rightCameraExtrinsicsLen = IntArray(1)
        pxrCapture?.getCameraExtrinsics(
            leftCameraExtrinsics, leftCameraExtrinsicsLen,
            rightCameraExtrinsics, rightCameraExtrinsicsLen
        )

        Log.i(
            TAG,
            "cameraIntrinsics len: " + len[0] + " value: " + cameraIntrinsics.contentToString()
        )
        Log.i(
            TAG,
            "leftCameraExtrinsics len: " + leftCameraExtrinsicsLen[0] + " value: " + leftCameraExtrinsics.contentToString()
        )
        Log.i(
            TAG,
            "rightCameraExtrinsics len: " + rightCameraExtrinsicsLen[0] + " value: " + rightCameraExtrinsics.contentToString()
        )

        val cameraParam = "cameraIntrinsics len:${len[0]}, value:${cameraIntrinsics.contentToString()}\n" +
                "leftCameraExtrinsics len:${leftCameraExtrinsicsLen[0]}, value:${leftCameraExtrinsics.contentToString()}\n" +
                "rightCameraExtrinsics len:${leftCameraExtrinsicsLen[0]}, value:${rightCameraExtrinsics.contentToString()}"
        tvCameraParam.text = cameraParam
    }

    private fun takePicture(){
        pxrCapture?.stopPreview()
        pxrCapture?.stopRecord()
        val mParentDir = Environment.getExternalStorageDirectory()
            .absolutePath + MainActivity.PATH_ON_PICTURE_STORAGE
        val fileName = "DemoPicture-${System.currentTimeMillis()}.jpeg"
        val filePath = mParentDir + fileName
        Log.i(TAG, "takePicture filePath: $filePath")
        pxrCapture?.takePicture(filePath)
    }

    private fun startRecord(){
        pxrCapture?.stopRecord()
        pxrCapture?.stopPreview()
        val mParentDir = Environment.getExternalStorageDirectory()
            .absolutePath + PATH_ON_MOVIES_STORAGE
        val fileName = "DemoPicture-${System.currentTimeMillis()}.mp4"
        val filePath = mParentDir + fileName
        Log.i(TAG, "startRecord filePath: $filePath")
        pxrCapture?.startRecord(filePath)
    }

    private fun stopRecord(){
        pxrCapture?.stopRecord()
    }

    private fun startPreview(){
        pxrCapture?.startPreview(surfaceView.holder.surface, PXRCapture.PXRCaptureRenderMode.PXRCapture_RenderMode_3D.ordinal, surfaceView.width, surfaceView.height)
    }

    private fun stopPreview(){
        pxrCapture?.stopPreview()
    }
    
    private fun initVideoEncoder() {
        try {
            videoEncoder = VideoEncoder()
            videoEncoder?.setCallback(object : VideoEncoder.EncoderCallback {
                override fun onEncodedData(data: ByteBuffer, info: MediaCodec.BufferInfo) {
                    // 处理编码后的数据
                    Log.i(TAG, "Encoded data received: ${info.size} bytes, flags: ${info.flags}")
                }
                
                override fun onEncoderError(error: Exception) {
                    Log.e(TAG, "Encoder error: ${error.message}")
                }
                
                override fun onEncoderStarted() {
                    Log.i(TAG, "Encoder started")
                }
                
                override fun onEncoderStopped() {
                    Log.i(TAG, "Encoder stopped")
                }
            })
            
            // 配置编码器参数
            videoEncoder?.prepare(VIDEO_WIDTH, VIDEO_HEIGHT, 60, 10 * 1024 * 1024)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init video encoder: ${e.message}")
        }
    }
    
    private fun startEncode() {
        if (videoEncoder == null) {
            initVideoEncoder()
        }
        
        try {
            // 连接到服务器（这里使用模拟地址，实际应用中需要替换为真实服务器地址）
            // videoEncoder?.connectToServer("192.168.1.100", 8888)
            
            // 启动编码
            videoEncoder?.startEncoding()
            
            // 获取编码器的输入Surface
            val encoderSurface = videoEncoder?.getInputSurface()
            
            if (encoderSurface != null) {
                // 使用编码器的Surface代替SurfaceView进行预览
                pxrCapture?.startPreview(encoderSurface, PXRCapture.PXRCaptureRenderMode.PXRCapture_RenderMode_3D.ordinal, VIDEO_WIDTH, VIDEO_HEIGHT)
                isEncodingEnabled = true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start encoding: ${e.message}")
        }
    }
    
    private fun stopEncode() {
        try {
            // 停止预览
            pxrCapture?.stopPreview()
            
            // 停止编码
            videoEncoder?.stopEncoding()
            isEncodingEnabled = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop encoding: ${e.message}")
        }
    }

    companion object {
        const val TAG = "PXRMainActivity"
        const val RECORD_MODE: Int = 0
        const val PREVIEW_MODE: Int = 1
        const val PERFORMANCE_MODE: Int = 2
        const val VIDEO_WIDTH: Int = 2048
        const val VIDEO_HEIGHT: Int = 1536
        const val VIDEO_H_FOV: Double = 76.35
        const val VIDEO_V_FOV: Double = 61.05

        const val PATH_ON_MOVIES_STORAGE = "/Movies/"
        const val PATH_ON_PICTURE_STORAGE = "/Pictures/"
    }

}