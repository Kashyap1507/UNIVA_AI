package com.kashyap.wizlyai.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of NetworkMonitor
 *
 * Note: This is a basic implementation that provides the same API as Android.
 * The actual network monitoring is handled by IOSNetworkMonitor.swift in the iOS app.
 * This Kotlin wrapper provides a consistent cross-platform API.
 *
 * For now, it assumes the network is always available.
 * Future enhancement: integrate with IOSNetworkMonitor.swift via callback bridge.
 *
 * @constructor Creates NetworkMonitor instance and starts monitoring
 */
actual class NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Observable network connectivity status
     * - true: Device has active internet connection
     * - false: Device is offline
     */
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        println("🌐 iOS NetworkMonitor (Kotlin): Initialized")
        println("   Note: Using basic implementation (always online)")
        println("   Note: IOSNetworkMonitor.swift handles actual network monitoring")
    }

    /**
     * Check if device is currently online
     *
     * @return true if device has active internet connection
     */
    actual fun isCurrentlyOnline(): Boolean {
        return _isOnline.value
    }

    /**
     * Unregister network callback (call on cleanup)
     *
     * This should be called when NetworkMonitor is no longer needed
     * to prevent memory leaks.
     */
    actual fun cleanup() {
        scope.cancel()
        println("🧹 iOS NetworkMonitor (Kotlin): Cleaned up")
    }
}
