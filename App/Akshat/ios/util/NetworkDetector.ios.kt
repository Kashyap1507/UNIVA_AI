package com.kashyap.wizlyai.util

import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS implementation of Network Detector
 *
 * Detects network connectivity status and connection type (WiFi vs Cellular).
 * Used to implement cellular data sync control based on user settings.
 *
 * Features:
 * - Check if device is online
 * - Detect network type (WiFi, Cellular, Ethernet, etc.)
 * - Check if currently on cellular data
 * - Respect user's cellular sync setting
 * - Metered connection detection
 *
 * iOS Implementation:
 * Uses NWPathMonitor (Network framework) via Swift bridge for real-time
 * network status monitoring with efficient system callbacks.
 *
 * Usage:
 * ```
 * val networkDetector = NetworkDetector()
 *
 * if (!networkDetector.isOnline()) {
 *     // Handle offline state
 * } else if (networkDetector.isOnCellular()) {
 *     // On cellular data - may want to skip large uploads
 * } else if (networkDetector.isOnWiFi()) {
 *     // On WiFi - safe to upload large files
 * }
 *
 * // Check if sync should proceed
 * val canSync = networkDetector.shouldSync(cellularSyncEnabled = false)
 * ```
 */
class NetworkDetector {
    companion object {
        private const val TAG = "NetworkDetector"
    }

    /**
     * Check if device has any active network connection
     *
     * @return true if online, false if offline
     */
    fun isOnline(): Boolean {
        return try {
            val callbacks = IOSCallbackBridge.getNetworkDetectorCallbacks()
            if (callbacks == null) {
                println("⚠️ $TAG: NetworkDetector callbacks not set!")
                return false // Safe default: assume offline
            }

            var online = false
            callbacks.isOnline { result ->
                online = result
            }

            online
        } catch (e: Exception) {
            println("❌ $TAG: Error checking network status: ${e.message}")
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
            val callbacks = IOSCallbackBridge.getNetworkDetectorCallbacks()
            if (callbacks == null) {
                println("⚠️ $TAG: NetworkDetector callbacks not set!")
                return false
            }

            var onWiFi = false
            callbacks.isOnWiFi { result ->
                onWiFi = result
            }

            onWiFi
        } catch (e: Exception) {
            println("❌ $TAG: Error checking WiFi status: ${e.message}")
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
            val callbacks = IOSCallbackBridge.getNetworkDetectorCallbacks()
            if (callbacks == null) {
                println("⚠️ $TAG: NetworkDetector callbacks not set!")
                return false
            }

            var onCellular = false
            callbacks.isOnCellular { result ->
                onCellular = result
            }

            onCellular
        } catch (e: Exception) {
            println("❌ $TAG: Error checking cellular status: ${e.message}")
            false
        }
    }

    /**
     * Check if device is on Ethernet connection
     *
     * Note: Rare on iOS devices, but possible with adapters
     *
     * @return true if connected via Ethernet, false otherwise
     */
    fun isOnEthernet(): Boolean {
        return try {
            val callbacks = IOSCallbackBridge.getNetworkDetectorCallbacks()
            if (callbacks == null) {
                println("⚠️ $TAG: NetworkDetector callbacks not set!")
                return false
            }

            var onEthernet = false
            callbacks.isOnEthernet { result ->
                onEthernet = result
            }

            onEthernet
        } catch (e: Exception) {
            println("❌ $TAG: Error checking Ethernet status: ${e.message}")
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
     * iOS Implementation Note:
     * iOS Network framework can detect expensive/constrained paths.
     * Cellular is always considered metered.
     *
     * @return true if network is metered (counts against data quota), false otherwise
     */
    fun isMeteredConnection(): Boolean {
        return try {
            val callbacks = IOSCallbackBridge.getNetworkDetectorCallbacks()
            if (callbacks == null) {
                println("⚠️ $TAG: NetworkDetector callbacks not set!")
                return true // Safe default: assume metered to be conservative
            }

            var isMetered = true
            callbacks.isMeteredConnection { result ->
                isMetered = result
            }

            isMetered
        } catch (e: Exception) {
            println("❌ $TAG: Error checking metered status: ${e.message}")
            true // Safe default: assume metered to be conservative
        }
    }

    /**
     * Check if sync should proceed based on network conditions
     *
     * This is a convenience method that combines:
     * - Online check
     * - Cellular check
     * - Cellular sync setting from AppSettings
     *
     * Logic:
     * 1. If offline -> BLOCK sync
     * 2. If on cellular AND cellular sync disabled -> BLOCK sync
     * 3. If on WiFi/Ethernet OR (cellular AND sync enabled) -> ALLOW sync
     *
     * @param cellularSyncEnabled User's cellular sync setting (true = allow cellular)
     * @return true if sync can proceed, false if should wait for WiFi
     */
    fun shouldSync(cellularSyncEnabled: Boolean): Boolean {
        if (!isOnline()) {
            println("🔴 $TAG: Sync blocked - Device is offline")
            return false
        }

        if (isOnCellular() && !cellularSyncEnabled) {
            println("🔴 $TAG: Sync blocked - On cellular data and cellular sync disabled")
            return false
        }

        // Online and either on WiFi, or on cellular with sync enabled
        val networkType = getNetworkType()
        println("✅ $TAG: Sync allowed - Connected via $networkType")
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
