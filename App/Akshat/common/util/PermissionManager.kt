package com.kashyap.wizlyai.util

/**
 * Common interface for handling runtime permissions across platforms
 */
expect class PermissionManager {
    /**
     * Check if camera permission is granted
     */
    fun isCameraPermissionGranted(): Boolean

    /**
     * Request camera permission
     * @param onGranted Callback when permission is granted
     * @param onDenied Callback when permission is denied
     */
    fun requestCameraPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    )

    /**
     * Check if user should be shown permission rationale
     */
    fun shouldShowCameraRationale(): Boolean

    /**
     * Open app settings (for when permission is permanently denied)
     */
    fun openAppSettings()
}

/**
 * Permission states
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    NOT_REQUESTED,
}
