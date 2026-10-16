package com.kashyap.wizlyai.notifications

/**
 * iOS implementation of NotificationPermissionBridge
 * Bridges to Swift IOSNotificationPermissionManager
 */
actual object NotificationPermissionBridge {
    private var permissionCallback: ((Boolean) -> Unit)? = null

    /**
     * Set Swift callback handler
     * Called from Swift when permission result is ready
     */
    fun onPermissionResult(granted: Boolean) {
        permissionCallback?.invoke(granted)
        permissionCallback = null
    }

    /**
     * Check if notification permission is granted
     * Calls into Swift IOSNotificationPermissionManager
     */
    actual fun hasPermission(): Boolean {
        return IOSNotificationPermissionCallbacks.hasPermission()
    }

    /**
     * Request notification permission
     * Calls into Swift IOSNotificationPermissionManager
     */
    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        permissionCallback = onResult
        IOSNotificationPermissionCallbacks.requestPermission()
    }

    /**
     * Open app settings
     * Calls into Swift IOSNotificationPermissionManager
     */
    actual fun openAppSettings() {
        IOSNotificationPermissionCallbacks.openAppSettings()
    }
}
