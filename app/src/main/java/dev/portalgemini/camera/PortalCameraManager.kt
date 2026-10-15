package dev.portalgemini.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

class PortalCameraManager(
    private val context: Context,
    private val onFrameJpeg: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "PortalCameraManager"
        const val DEFAULT_CAMERA_ID = "0" // 720p dewarped Smart Camera on Portal
        const val PREVIEW_WIDTH = 1280
        const val PREVIEW_HEIGHT = 720
        const val CAPTURE_WIDTH = 640
        const val CAPTURE_HEIGHT = 480
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive

    private val _isStreamingFrames = MutableStateFlow(false)
    val isStreamingFrames: StateFlow<Boolean> = _isStreamingFrames

    private val _cameraMode = MutableStateFlow(CameraMode.STANDBY)
    val cameraMode: StateFlow<CameraMode> = _cameraMode

    var autoSwitchEnabled: Boolean = true

    var frameIntervalMs: Long = 1000L
    private var lastFrameTimeMs: Long = 0L

    var cameraId: String = DEFAULT_CAMERA_ID

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startCamera(textureView: TextureView) {
        if (textureView.isAvailable) {
            openCameraWithTexture(textureView.surfaceTexture!!)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCameraWithTexture(surface)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    closeCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraWithTexture(surfaceTexture: SurfaceTexture) {
        surfaceTexture.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
        previewSurface = Surface(surfaceTexture)

        setupImageReader()

        try {
            Log.i(TAG, "Opening Camera ID $cameraId directly via Camera2")
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "Camera $cameraId successfully opened")
                    cameraDevice = camera
                    _isCameraActive.value = true
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera $cameraId disconnected")
                    camera.close()
                    cameraDevice = null
                    _isCameraActive.value = false
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera $cameraId error: $error")
                    camera.close()
                    cameraDevice = null
                    _isCameraActive.value = false
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera $cameraId", e)
        }
    }

    private fun setupImageReader() {
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            CAPTURE_WIDTH,
            CAPTURE_HEIGHT,
            ImageFormat.YUV_420_888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    val shouldStream = _isStreamingFrames.value && _cameraMode.value != CameraMode.STANDBY
                    if (shouldStream && (now - lastFrameTimeMs) >= frameIntervalMs) {
                        val jpegBytes = imageToJpeg(image)
                        if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                            lastFrameTimeMs = now
                            onFrameJpeg(jpegBytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring camera image", e)
                } finally {
                    image.close()
                }
            }, backgroundHandler)
        }
    }

    private fun imageToJpeg(image: Image): ByteArray? {
        return try {
            val nv21 = yuv420888ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 70, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to JPEG", e)
            null
        }
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var pos = 0
        if (yPixelStride == 1 && yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get()
                    if (yPixelStride > 1 && col < width - 1) {
                        yBuffer.position(yBuffer.position() + yPixelStride - 1)
                    }
                }
            }
        }

        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[pos++] = vBuffer.get(vIndex)
                nv21[pos++] = uBuffer.get(uIndex)
            }
        }
        return nv21
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val preview = previewSurface ?: return
        val reader = imageReader ?: return

        try {
            val surfaces = listOf(preview, reader.surface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(preview)
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        Log.i(TAG, "Camera preview session repeating request running")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start camera repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera capture session", e)
        }
    }

    fun setCameraMode(mode: CameraMode) {
        _cameraMode.value = mode
        _isStreamingFrames.value = (mode != CameraMode.STANDBY)
        Log.i(TAG, "Camera mode set to: $mode")
    }

    fun triggerAutoCamera() {
        if (_cameraMode.value == CameraMode.STANDBY) {
            setCameraMode(CameraMode.AUTO)
        } else {
            setCameraMode(CameraMode.STANDBY)
        }
    }

    fun setStreamingFrames(enable: Boolean) {
        setCameraMode(if (enable) CameraMode.CONTINUOUS else CameraMode.STANDBY)
    }

    fun toggleStreaming(): Boolean {
        return if (_cameraMode.value == CameraMode.STANDBY) {
            setCameraMode(CameraMode.CONTINUOUS)
            true
        } else {
            setCameraMode(CameraMode.STANDBY)
            false
        }
    }

    fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            previewSurface?.release()
            previewSurface = null
            _isCameraActive.value = false
            Log.i(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
    }
}
