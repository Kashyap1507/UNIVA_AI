package com.kashyap.wizlyai.notifications

/**
 * Cross-platform bridge for checking and requesting notification permissions
 */
expect object NotificationPermissionBridge {
    /**
     * Check if notification permission is granted
     * @return true if notifications are enabled, false otherwise
     */
    fun hasPermission(): Boolean

    /**
     * Request notification permission from the user
     * @param onResult Callback with true if granted, false if denied
     */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * Open device settings to notification page for this app
     */
    fun openAppSettings()
}
