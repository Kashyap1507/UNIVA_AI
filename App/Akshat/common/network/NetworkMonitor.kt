package com.kashyap.wizlyai.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors network connectivity and triggers sync when connection is restored
 *
 * Features:
 * - Real-time network status monitoring
 * - Automatic document sync when network becomes available
 * - StateFlow for reactive UI updates
 * - Efficient with system callbacks (no polling)
 *
 * Usage:
 * ```kotlin
 * // Android
 * val networkMonitor = NetworkMonitor(context)
 *
 * // iOS
 * val networkMonitor = NetworkMonitor()
 *
 * // Observe network status in UI
 * networkMonitor.isOnline.collectAsState()
 * ```
 */
expect class NetworkMonitor {
    /**
     * Observable network connectivity status
     * - true: Device has active internet connection
     * - false: Device is offline
     */
    val isOnline: StateFlow<Boolean>

    /**
     * Check if device is currently online
     *
     * @return true if device has active internet connection
     */
    fun isCurrentlyOnline(): Boolean

    /**
     * Unregister network callback (call on cleanup)
     *
     * This should be called when NetworkMonitor is no longer needed
     * to prevent memory leaks.
     */
    fun cleanup()
}
