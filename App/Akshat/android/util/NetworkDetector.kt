package com.kashyap.wizlyai.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

/**
 * Network Detector Utility
 *
 * Detects network connectivity status and connection type (WiFi vs Cellular).
 * Used to implement cellular data sync control.
 *
 * Features:
 * - Check if device is online
 * - Detect network type (WiFi, Cellular, Ethernet, etc.)
 * - Check if currently on cellular data
 * - Support for Android API 23+ (Marshmallow and newer)
 *
 * Usage:
 * ```
 * val networkDetector = NetworkDetector(context)
 *
 * if (!networkDetector.isOnline()) {
 *     // Handle offline state
 * } else if (networkDetector.isOnCellular()) {
 *     // On cellular data - may want to skip large uploads
 * } else if (networkDetector.isOnWiFi()) {
 *     // On WiFi - safe to upload large files
 * }
 * ```
 */
class NetworkDetector(private val context: Context) {
    companion object {
        private const val TAG = "NetworkDetector"
    }

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Check if device has any active network connection
     *
     * @return true if online, false if offline
     */
    fun isOnline(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network status", e)
            false // Safe default: assume offline
        }
    }

    /**
     * Check if device is currently on WiFi
     *
     * @return true if connected to WiFi, false otherwise
     */
    fun isOnWiFi(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi status", e)
            false
        }
    }

    /**
     * Check if device is currently on cellular data
     *
     * Detects all types of cellular connections:
     * - 2G/EDGE
     * - 3G/HSPA
     * - 4G/LTE
     * - 5G
     *
     * @return true if connected via cellular, false otherwise
     */
    fun isOnCellular(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_MOBILE && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cellular status", e)
            false
        }
    }

    /**
     * Check if device is on Ethernet connection
     *
     * @return true if connected via Ethernet, false otherwise
     */
    fun isOnEthernet(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_ETHERNET && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Ethernet status", e)
            false
        }
    }

    /**
     * Get human-readable network type string
     *
     * @return Network type name ("WiFi", "Cellular", "Ethernet", "Unknown", "Offline")
     */
    fun getNetworkType(): String {
        return when {
            !isOnline() -> "Offline"
            isOnWiFi() -> "WiFi"
            isOnCellular() -> "Cellular"
            isOnEthernet() -> "Ethernet"
            else -> "Unknown"
        }
    }

    /**
     * Check if current network is metered (cellular or WiFi with data limits)
     *
     * @return true if network is metered (counts against data quota), false otherwise
     */
    fun isMeteredConnection(): Boolean {
        return try {
            connectivityManager.isActiveNetworkMetered
        } catch (e: Exception) {
            Log.e(TAG, "Error checking metered status", e)
            true // Safe default: assume metered to be conservative
        }
    }

    /**
     * Check if sync should proceed based on network conditions
     *
     * This is a convenience method that combines:
     * - Online check
     * - Cellular check
     * - Cellular sync setting
     *
     * @param cellularSyncEnabled User's cellular sync setting (true = allow cellular)
     * @return true if sync can proceed, false if should wait for WiFi
     */
    fun shouldSync(cellularSyncEnabled: Boolean): Boolean {
        if (!isOnline()) {
            Log.d(TAG, "Sync blocked: Device is offline")
            return false
        }

        if (isOnCellular() && !cellularSyncEnabled) {
            Log.d(TAG, "Sync blocked: On cellular data and cellular sync disabled")
            return false
        }

        // Online and either on WiFi, or on cellular with sync enabled
        val networkType = getNetworkType()
        Log.d(TAG, "Sync allowed: Connected via $networkType")
        return true
    }

    /**
     * Get detailed network info for logging/debugging
     *
     * @return Map with network status details
     */
    fun getNetworkInfo(): Map<String, Any> {
        return mapOf(
            "online" to isOnline(),
            "networkType" to getNetworkType(),
            "onWiFi" to isOnWiFi(),
            "onCellular" to isOnCellular(),
            "onEthernet" to isOnEthernet(),
            "isMetered" to isMeteredConnection(),
        )
    }
}
