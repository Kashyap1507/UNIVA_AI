package com.kashyap.wizlyai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.kashyap.wizlyai.offline.OfflineQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android implementation of NetworkMonitor
 * Monitors network connectivity and triggers sync when connection is restored
 *
 * Features:
 * - Real-time network status monitoring
 * - Automatic document sync when network becomes available
 * - UX-017: Automatic offline queue processing when network restored
 * - StateFlow for reactive UI updates
 * - Efficient with system callbacks (no polling)
 *
 * Usage:
 * ```kotlin
 * // Initialize in MainActivity
 * val networkMonitor = NetworkMonitor(this, offlineQueueManager)
 *
 * // Observe network status in UI
 * networkMonitor.isOnline.collectAsState()
 * ```
 *
 * @param context Android context for accessing ConnectivityManager
 * @param offlineQueueManager Optional queue manager for processing offline operations
 */
actual class NetworkMonitor(
    context: Context,
    private val offlineQueueManager: OfflineQueueManager? = null,
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val _isOnline = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Observable network connectivity status
     * - true: Device has active internet connection
     * - false: Device is offline
     */
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "📡 Network available: $network")
                val wasOffline = !_isOnline.value
                _isOnline.value = true

                // If we just came online, trigger sync
                if (wasOffline) {
                    Log.d(TAG, "🔄 Device came online - triggering document sync")
                    triggerSync()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "📡 Network lost: $network")
                _isOnline.value = false
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                Log.d(TAG, "📡 Network capabilities changed:")
                Log.d(TAG, "   Has Internet: $hasInternet")
                Log.d(TAG, "   Validated: $hasValidated")

                _isOnline.value = hasInternet && hasValidated
            }
        }

    init {
        // Check initial network state
        val activeNetwork = connectivityManager?.activeNetwork
        val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasValidated = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        _isOnline.value = hasInternet && hasValidated

        Log.d(TAG, "🌐 NetworkMonitor initialized")
        Log.d(TAG, "   Initial online status: ${_isOnline.value}")

        // Register for network updates
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()

        try {
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
            Log.d(TAG, "✅ Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register network callback", e)
        }
    }

    /**
     * Trigger document sync to backend
     *
     * This is called automatically when network becomes available,
     * ensuring offline-created documents are uploaded as soon as possible.
     *
     * UX-017: Also triggers offline queue processing for pending operations
     */
    private fun triggerSync() {
        try {
            // UX-017: Process offline queue first
            offlineQueueManager?.let { queueManager ->
                scope.launch {
                    Log.d(TAG, "📋 Triggering offline queue processing...")
                    queueManager.onConnectivityRestored()
                }
            }

            // Trigger document sync via BootstrapSyncCoordinator
            com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.syncDocuments()
            Log.d(TAG, "✅ Document sync triggered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to trigger sync", e)
        }
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
     * to prevent memory leaks. Typically called in onDestroy().
     */
    actual fun cleanup() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "🧹 Network callback unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to unregister network callback", e)
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
