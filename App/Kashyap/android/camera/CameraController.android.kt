package com.kashyap.wizlyai.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Android CameraX implementation
 */
actual class CameraController(
    private val context: Context,
    private val activity: ComponentActivity,
) {
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isFlashOn: Boolean = false
    private var previewView: PreviewView? = null
    private var captureCallback: ((String) -> Unit)? = null
    private var captureErrorCallback: ((String) -> Unit)? = null
    private var targetScreenWidth: Int = 0
    private var targetScreenHeight: Int = 0

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    actual fun initialize(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
    ) {
        if (hasPermission()) {
            onPermissionGranted()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS,
            )
            // Note: Result should be handled in activity's onRequestPermissionsResult
        }
    }

    /**
     * Set the preview view to display camera feed
     * Must be called before startPreview()
     */
    fun setPreviewView(view: PreviewView) {
        this.previewView = view
    }

    @OptIn(ExperimentalZeroShutterLag::class)
    actual fun startPreview() {
        val view = previewView ?: return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Get screen resolution for matching camera resolution
            val displayMetrics = context.resources.displayMetrics
            targetScreenWidth = displayMetrics.widthPixels
            targetScreenHeight = displayMetrics.heightPixels
            val targetResolution = android.util.Size(targetScreenWidth, targetScreenHeight)

            // Preview with screen-matched resolution
            val preview =
                Preview.Builder()
                    .setTargetResolution(targetResolution)
                    .build()
                    .also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

            // ImageAnalysis to capture frames from preview at screen resolution
            // This ensures captured image resolution matches what's displayed on screen
            imageAnalysis =
                ImageAnalysis.Builder()
                    .setTargetResolution(targetResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind all use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                camera =
                    cameraProvider?.bindToLifecycle(
                        activity,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                    )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    actual fun stopPreview() {
        cameraProvider?.unbindAll()
    }

    actual fun capturePhoto(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val analysis =
            imageAnalysis ?: run {
                onError("Camera not initialized")
                return
            }

        // Store callbacks for use in analyzer
        captureCallback = onSuccess
        captureErrorCallback = onError

        // Set analyzer to capture one frame from preview
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                // Convert ImageProxy to Bitmap
                val bitmap = imageProxyToBitmap(imageProxy)

                if (bitmap != null) {
                    // Create output file
                    val photoFile =
                        File(
                            context.cacheDir,
                            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                                .format(System.currentTimeMillis()) + ".jpg",
                        )

                    // Save bitmap to file
                    FileOutputStream(photoFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    // Clear analyzer to stop capturing frames
                    analysis.clearAnalyzer()

                    // Call success callback on main thread
                    ContextCompat.getMainExecutor(context).execute {
                        captureCallback?.invoke(photoFile.absolutePath)
                        captureCallback = null
                        captureErrorCallback = null
                    }
                } else {
                    // Clear analyzer
                    analysis.clearAnalyzer()

                    // Call error callback on main thread
                    ContextCompat.getMainExecutor(context).execute {
                        captureErrorCallback?.invoke("Failed to convert image")
                        captureCallback = null
                        captureErrorCallback = null
                    }
                }
            } catch (e: Exception) {
                // Clear analyzer
                analysis.clearAnalyzer()

                // Call error callback on main thread
                ContextCompat.getMainExecutor(context).execute {
                    captureErrorCallback?.invoke(e.message ?: "Unknown error")
                    captureCallback = null
                    captureErrorCallback = null
                }
            } finally {
                imageProxy.close()
            }
        }
    }

    /**
     * Convert ImageProxy to Bitmap and resize to exact screen dimensions
     * Handles YUV_420_888 format from camera with proper NV21 conversion
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val planes = imageProxy.planes
        val width = imageProxy.width
        val height = imageProxy.height

        // NV21 format requires: width * height for Y + width * height / 2 for UV
        val nv21 = ByteArray(width * height + width * height / 2)

        // Copy Y plane with stride handling
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var pos = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride)
            }
        }

        // Copy UV plane with proper VU interleaving for NV21
        val vPlane = planes[2]  // V plane
        val uPlane = planes[1]  // U plane
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val uvRowStride = vPlane.rowStride
        val uvPixelStride = vPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val offset = row * uvRowStride + col * uvPixelStride
                // NV21 format: VUVUVU... (V first, then U)
                nv21[pos++] = vBuffer.get(offset)
                nv21[pos++] = uBuffer.get(offset)
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate bitmap if needed based on image rotation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // Resize bitmap to exact screen dimensions if they don't match
        // After rotation, check which dimension to use based on rotation
        val targetWidth: Int
        val targetHeight: Int

        if (rotationDegrees == 90 || rotationDegrees == 270) {
            // Portrait mode: swap dimensions
            targetWidth = targetScreenWidth
            targetHeight = targetScreenHeight
        } else {
            // Landscape mode: use as-is
            targetWidth = targetScreenWidth
            targetHeight = targetScreenHeight
        }

        // Always resize to exact screen dimensions
        if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }

        return bitmap
    }

    actual fun hasPermission(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    actual fun setFlashEnabled(enabled: Boolean) {
        isFlashOn = enabled
        camera?.cameraControl?.enableTorch(enabled)
    }

    actual fun isFlashEnabled(): Boolean {
        return isFlashOn
    }

    actual fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
