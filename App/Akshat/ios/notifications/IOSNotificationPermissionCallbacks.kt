package com.kashyap.wizlyai.notifications

/**
 * iOS callback bridge implementation
 * Connects Kotlin to Swift IOSNotificationPermissionManager
 */
object IOSNotificationPermissionCallbacks {
    private var swiftCallbacks: IOSNotificationPermissionCallbacksProtocol? = null

    /**
     * Initialize with Swift callback implementation
     * Called from Swift AppDelegate
     */
    fun initialize(callbacks: IOSNotificationPermissionCallbacksProtocol) {
        swiftCallbacks = callbacks
    }

    fun hasPermission(): Boolean {
        return swiftCallbacks?.hasPermission() ?: false
    }

    fun requestPermission() {
        swiftCallbacks?.requestPermission()
    }

    fun openAppSettings() {
        swiftCallbacks?.openAppSettings()
    }
}

/**
 * Protocol that Swift must implement
 */
interface IOSNotificationPermissionCallbacksProtocol {
    fun hasPermission(): Boolean

    fun requestPermission()

    fun openAppSettings()
}
