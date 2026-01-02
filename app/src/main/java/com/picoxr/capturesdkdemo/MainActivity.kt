package com.picoxr.capturesdkdemo

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.media.ImageReader
import android.media.MediaCodec
import android.os.Bundle

import android.provider.Settings.Global
import android.text.InputType
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.picoxr.capturesdkdemo.permissions.PermissionUtils
import com.pxr.capturelib.PXRCapture
import com.pxr.capturelib.PXRCaptureCallBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var imageReader: ImageReader

    private var pxrCapture: PXRCapture? = null
    private var videoEncoder: VideoEncoder? = null
    private var isEncodingEnabled: Boolean = false



    private lateinit var btnStartPreview: Button
    private lateinit var btnStopPreview: Button
    private lateinit var btnGetCameraParam:Button
    private lateinit var btnStartEncode: Button
    private lateinit var btnStopEncode: Button
    private lateinit var tvCameraParam:TextView
    private lateinit var tvIpDetection:TextView
    private lateinit var checkBox:CheckBox
    private lateinit var surfaceView: SurfaceView

    private var captureState:Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(TAG, "onCreate")
        surfaceView = findViewById(R.id.surfaceView)

        btnStartPreview = findViewById(R.id.btn_start_preview)
        btnStopPreview = findViewById(R.id.btn_stop_preview)

        btnGetCameraParam = findViewById(R.id.btn_get_camera_param)
        btnStartEncode = findViewById(R.id.btn_start_encode)
        btnStopEncode = findViewById(R.id.btn_stop_encode)
        tvCameraParam = findViewById(R.id.tv_camera_param)
        tvIpDetection = findViewById(R.id.tv_ip_detection)
        checkBox = findViewById(R.id.check_box)


        btnStartPreview.setOnClickListener { startPreview() }
        btnStopPreview.setOnClickListener { stopPreview() }

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
            // Auto get USB tethering IP address
            val usbIp = getUsbTetheringIpAddress()
            
            // Create input dialog to get server IP address
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Connect to Server")
            builder.setMessage("Please enter server IP address:")
            
            // Set up input field
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT
            
            // If USB IP is found, auto fill the input field
            if (usbIp != null) {
                input.setText(usbIp)
                input.hint = "e.g: 192.168.1.100 (Auto-filled USB tethering IP)"
            } else {
                input.hint = "e.g: 192.168.1.100"
            }
            
            builder.setView(input)
            
            // Set dialog buttons
            builder.setPositiveButton("Connect", DialogInterface.OnClickListener { _, _ ->
                val serverIp = input.text.toString().trim()
                if (serverIp.isNotEmpty()) {
                    // Update status on UI thread
                    runOnUiThread {
                        tvIpDetection.text = "Connecting to $serverIp:12345..."
                    }
                    
                    // Use coroutine for network connection to avoid blocking UI
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            // Connect to server (fixed port 12345)
                            videoEncoder?.connectToServer(serverIp, 12345)
                            
                            // Connection successful, update UI
                            runOnUiThread {
                                tvIpDetection.text = "TCP connection successful! Starting data transmission..."
                            }
                            
                            // Start encoding
                            videoEncoder?.startEncoding()
                            
                            // Get encoder input Surface
                            val encoderSurface = videoEncoder?.getInputSurface()
                            
                            if (encoderSurface != null) {
                                // Use encoder Surface instead of SurfaceView for preview
                                pxrCapture?.startPreview(encoderSurface, PXRCapture.PXRCaptureRenderMode.PXRCapture_RenderMode_3D.ordinal, VIDEO_WIDTH, VIDEO_HEIGHT)
                                isEncodingEnabled = true
                                
                                runOnUiThread {
                                    tvIpDetection.text = "Connected to $serverIp:12345, data transmission in progress..."
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to connect or start encoding: ${e.message}")
                            runOnUiThread {
                                tvIpDetection.text = "TCP connection failed: ${e.message}"
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        tvIpDetection.text = "Please enter a valid IP address"
                    }
                }
            })
            
            builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, _ ->
                dialog.cancel()
                runOnUiThread {
                    tvIpDetection.text = "Connection canceled"
                }
            })
            
            builder.show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start encoding: ${e.message}")
            runOnUiThread {
                tvIpDetection.text = "Operation failed: ${e.message}"
            }
        }
    }
    
    /**
     * Get USB tethering IP address
     * USB tethering typically uses 192.168.42.x network segment
     */
    private fun getUsbTetheringIpAddress(): String? {
        try {
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                
                // Check if it's a USB tethering network interface
                if (networkInterface.name.startsWith("rndis") || networkInterface.name.startsWith("usb")) {
                    val inetAddresses = networkInterface.inetAddresses
                    while (inetAddresses.hasMoreElements()) {
                        val inetAddress = inetAddresses.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                            val ipAddress = inetAddress.hostAddress
                            Log.i(TAG, "Found USB tethering IP: $ipAddress")
                            return ipAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting USB tethering IP: ${e.message}")
        }
        return null
    }
    
    private fun stopEncode() {
        try {
            // Stop preview
            pxrCapture?.stopPreview()
            
            // Stop encoding
            videoEncoder?.stopEncoding()
            isEncodingEnabled = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop encoding: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        
        try {
            // Stop encoding
            if (isEncodingEnabled) {
                stopEncode()
            }
            
            // Release VideoEncoder resources
            videoEncoder?.release()
            videoEncoder = null
            
            // Release PXRCapture resources
            if (pxrCapture != null) {
                pxrCapture?.stopPreview()
                pxrCapture?.release()
                pxrCapture = null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
    }

    companion object {
        const val TAG = "PXRMainActivity"
        const val RECORD_MODE: Int = 0
        const val PREVIEW_MODE: Int = 1
        const val PERFORMANCE_MODE: Int = 2
        const val VIDEO_WIDTH: Int = 4096
        const val VIDEO_HEIGHT: Int = 2048
        const val VIDEO_H_FOV: Double = 76.35
        const val VIDEO_V_FOV: Double = 61.05


    }

}