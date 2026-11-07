package com.kashyap.wizlyai.camera

/**
 * Common camera interface for both Android and iOS
 */
expect class CameraController {
    /**
     * Initialize camera with required permissions
     */
    fun initialize(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
    )

    /**
     * Start camera preview
     */
    fun startPreview()

    /**
     * Stop camera preview
     */
    fun stopPreview()

    /**
     * Capture photo and return file path
     */
    fun capturePhoto(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    )

    /**
     * Check if camera permission is granted
     */
    fun hasPermission(): Boolean

    /**
     * Enable or disable flash/torch mode
     */
    fun setFlashEnabled(enabled: Boolean)

    /**
     * Check if flash is currently enabled
     */
    fun isFlashEnabled(): Boolean

    /**
     * Release camera resources
     */
    fun release()
}

/**
 * Camera configuration
 */
data class CameraConfig(
    val enableFlash: Boolean = false,
    val imageQuality: Int = 90,
    val targetResolution: Resolution = Resolution.HIGH,
)

enum class Resolution {
    LOW, // 640x480
    MEDIUM, // 1280x720
    HIGH, // 1920x1080
}
