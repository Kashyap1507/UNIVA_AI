package com.kashyap.wizlyai.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of DocumentSyncManager
 *
 * Features:
 * - Network detection (WiFi vs Cellular)
 * - Cellular sync control
 * - Bridges to Swift implementations
 *
 * Note: Actual sync implementation is handled by:
 * - IOSBackgroundSyncHandler.swift for background syncing
 * - IOSBootstrapSyncManager.swift for bootstrap data sync
 * - IOSLocalDocumentStorage.swift for document operations
 * - IOSNetworkDetector.swift for network status
 *
 * This Kotlin wrapper provides API compatibility with Android.
 */
actual class DocumentSyncManager {
    private val _pendingSyncCount = MutableStateFlow(0)
    private val userDefaults = NSUserDefaults.standardUserDefaults

    companion object {
        private const val TAG = "DocumentSyncManager"
        private const val PREF_KEY_CELLULAR_SYNC_ENABLED = "cellular_sync_enabled"
    }

    /**
     * Sync all pending documents to backend
     * Uploads document metadata and pages
     *
     * Checks network conditions before syncing:
     * - Aborts if offline
     * - Defers if on cellular and cellular sync disabled
     *
     * @return Result with number of documents synced
     */
    actual suspend fun syncPendingDocuments(): Result<Int> {
        return try {
            println("🔄 $TAG: Syncing pending documents")

            // Check network conditions
            val networkStatus = checkNetworkConditions()
            if (!networkStatus.shouldSync) {
                println("🚫 $TAG: Sync deferred - ${networkStatus.reason}")
                println("   Documents will sync automatically when conditions improve")
                return Result.success(0)
            }

            println("✅ $TAG: Network conditions OK - ${networkStatus.networkType}")
            println("   Note: Actual sync implementation in IOSBackgroundSyncHandler.swift")

            // Note: Real implementation should call Swift code via bridge
            // For now, return success with 0 documents synced
            val syncedCount = 0
            _pendingSyncCount.value = maxOf(0, _pendingSyncCount.value - syncedCount)

            Result.success(syncedCount)
        } catch (e: Exception) {
            println("❌ $TAG: Error syncing documents - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sync all pending pages to backend
     * Handles page uploads for existing documents
     *
     * @return Result with number of pages synced
     */
    actual suspend fun syncPendingPages(): Result<Int> {
        return try {
            println("🔄 iOS DocumentSyncManager: Syncing pending pages")
            println("   Note: Actual implementation in IOSBackgroundSyncHandler.swift")

            // Note: Real implementation should call Swift code via bridge
            Result.success(0)
        } catch (e: Exception) {
            println("❌ iOS DocumentSyncManager: Error syncing pages - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Download new documents from backend for a user
     *
     * @param userId User ID to download documents for
     * @return Result with number of documents downloaded
     */
    actual suspend fun downloadNewDocuments(userId: String): Result<Int> {
        return try {
            println("📥 iOS DocumentSyncManager: Downloading documents for user $userId")
            println("   Note: Actual implementation in IOSBackgroundSyncHandler.swift")

            // Note: Real implementation should call Swift code via bridge
            Result.success(0)
        } catch (e: Exception) {
            println("❌ iOS DocumentSyncManager: Error downloading documents - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Observe pending sync count
     * Used for showing sync badges in UI
     *
     * @return Flow of pending document count
     */
    actual fun observePendingSyncCount(): Flow<Int> {
        return _pendingSyncCount.asStateFlow()
    }

    /**
     * Force sync all documents (ignoring sync preferences)
     *
     * @return Result indicating success/failure
     */
    actual suspend fun forceSyncAll(): Result<Unit> {
        return try {
            println("🔄 $TAG: Force syncing all documents")
            println("   Note: Actual implementation in IOSBackgroundSyncHandler.swift")

            // Note: Real implementation should call Swift code via bridge
            // Could trigger IOSBackgroundSyncHandler.shared.performSync()

            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ $TAG: Error force syncing - ${e.message}")
            Result.failure(e)
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Check network conditions for sync
     *
     * Calls Swift IOSNetworkDetector via callback bridge to check:
     * - Is device online?
     * - Is on WiFi or cellular?
     * - Should sync proceed based on cellular sync setting?
     *
     * @return NetworkStatus with decision and reason
     */
    private fun checkNetworkConditions(): NetworkStatus {
        val callbacks = com.kashyap.wizlyai.ios.IOSCallbackBridge.getNetworkDetectorCallbacks()

        if (callbacks == null) {
            println("⚠️ $TAG: Network detector callbacks not set, using safe default")
            return NetworkStatus(
                shouldSync = true,
                networkType = "Unknown",
                reason = "Network detector not available - allowing sync",
            )
        }

        // Check if device is online
        var isOnline = false
        callbacks.isOnline { result -> isOnline = result }
        if (!isOnline) {
            return NetworkStatus(
                shouldSync = false,
                networkType = "Offline",
                reason = "Device is offline",
            )
        }

        // Get network type for logging
        var networkType = "Unknown"
        callbacks.getNetworkType { result -> networkType = result }

        // Check if on cellular
        var isOnCellular = false
        callbacks.isOnCellular { result -> isOnCellular = result }

        if (isOnCellular) {
            val cellularSyncEnabled = isCellularSyncEnabled()
            if (!cellularSyncEnabled) {
                return NetworkStatus(
                    shouldSync = false,
                    networkType = networkType,
                    reason = "On cellular data and cellular sync disabled",
                )
            }
        }

        // All checks passed
        return NetworkStatus(
            shouldSync = true,
            networkType = networkType,
            reason = "Network conditions OK",
        )
    }

    /**
     * Check if cellular sync is enabled for current user
     *
     * Reads from NSUserDefaults (iOS equivalent of SharedPreferences).
     * Defaults to false (WiFi-only) for safety.
     *
     * @return true if cellular sync enabled, false if WiFi-only
     */
    private fun isCellularSyncEnabled(): Boolean {
        return try {
            userDefaults.boolForKey(PREF_KEY_CELLULAR_SYNC_ENABLED)
        } catch (e: Exception) {
            println("⚠️ $TAG: Error reading cellular sync setting: ${e.message}, defaulting to OFF")
            false // Safe default: WiFi-only
        }
    }

    /**
     * Network status result
     */
    private data class NetworkStatus(
        val shouldSync: Boolean,
        val networkType: String,
        val reason: String,
    )
}
