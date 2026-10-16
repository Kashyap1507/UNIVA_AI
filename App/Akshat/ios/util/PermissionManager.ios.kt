package com.kashyap.wizlyai.util

import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS implementation of PermissionManager
 * Uses Swift bridge to AVFoundation for camera permissions
 */
actual class PermissionManager {
    /**
     * Check if camera permission is granted
     */
    actual fun isCameraPermissionGranted(): Boolean {
        val callbacks = IOSCallbackBridge.getPermissionManagerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS PermissionManager: Callbacks not set, returning false")
            return false
        }
        return callbacks.isCameraPermissionGranted()
    }

    /**
     * Request camera permission
     */
    actual fun requestCameraPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    ) {
        val callbacks = IOSCallbackBridge.getPermissionManagerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS PermissionManager: Callbacks not set, calling onDenied")
            onDenied()
            return
        }
        callbacks.requestCameraPermission(onGranted, onDenied)
    }

    /**
     * Check if should show permission rationale
     * On iOS, this checks if permission was previously denied
     */
    actual fun shouldShowCameraRationale(): Boolean {
        val callbacks = IOSCallbackBridge.getPermissionManagerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS PermissionManager: Callbacks not set, returning false")
            return false
        }
        return callbacks.wasCameraPermissionDenied()
    }

    /**
     * Open app settings
     */
    actual fun openAppSettings() {
        val callbacks = IOSCallbackBridge.getPermissionManagerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS PermissionManager: Callbacks not set")
            return
        }
        callbacks.openAppSettings()
    }
}
