package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.ios.IOSCallbackBridge
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.BootstrapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.HiddenFromObjC

/**
 * iOS implementation of BootstrapSyncCoordinator
 *
 * This is the bridge between the common Auth code and the iOS-specific
 * CoreData/Swift sync infrastructure (IOSBootstrapSyncManager).
 *
 * Architecture:
 * - Kotlin (this file) → Swift IOSBootstrapSyncManager → CoreData
 * - IOSBootstrapSyncManager is implemented in iosApp/iosApp/Sync/
 * - Uses CoreData for local storage (WizlyDataModel.xcdatamodeld)
 */
@OptIn(ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
actual object BootstrapSyncCoordinator {
    /**
     * Sync bootstrap data after authentication
     *
     * Delegates to Swift IOSBootstrapSyncManager which:
     * 1. Saves user to CoreData
     * 2. Saves bootstrap data (student profile, settings) to CoreData
     * 3. Updates sync preferences (timestamps)
     * 4. Invalidates caches
     *
     * @param bootstrapData Bootstrap data from auth response
     * @param user Authenticated user
     */
    actual fun syncBootstrapData(
        bootstrapData: BootstrapData,
        user: AuthUser,
    ) {
        println("═══════════════════════════════════════════════════════")
        println("🔄 iOS: BootstrapSyncCoordinator.syncBootstrapData")
        println("═══════════════════════════════════════════════════════")
        println("👤 User: ${user.email} (ID: ${user.id})")
        println("📦 Bootstrap Data:")
        println("   - Sync Timestamp: ${bootstrapData.syncTimestamp}")
        println("═══════════════════════════════════════════════════════")

        try {
            // Call Swift IOSBootstrapSyncManager via BootstrapSyncBridge
            println("📝 iOS: Calling BootstrapSyncBridge...")
            BootstrapSyncBridge.processBootstrapData(bootstrapData, user.id)
            println("✅ iOS: Bootstrap data sync complete")
        } catch (e: Exception) {
            println("❌ iOS: Bootstrap sync failed: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Clear all synced data (called on logout)
     *
     * Clears:
     * - All CoreData entities
     * - All sync preferences (timestamps)
     * - All caches
     */
    @HiddenFromObjC
    actual suspend fun clearSyncData() =
        withContext(Dispatchers.Default) {
            println("🧹 iOS: BootstrapSyncCoordinator.clearSyncData")

            try {
                BootstrapSyncBridge.clearAllData()
                println("✅ iOS: Sync data cleared")
            } catch (e: Exception) {
                println("❌ iOS: Failed to clear sync data: ${e.message}")
                e.printStackTrace()
            }
        }

    /**
     * Trigger smart sync to check for updates
     *
     * This is called in response to:
     * - FCM push notifications (data changed on server)
     * - Periodic sync checks
     * - App foreground events
     * - Manual refresh requests
     *
     * Smart sync checks if sync is needed and invalidates caches
     * to trigger UI refresh if data has changed.
     *
     * @param force Force sync even if not needed based on time interval
     */
    @HiddenFromObjC
    actual suspend fun smartSync(force: Boolean) =
        withContext(Dispatchers.Default) {
            println("🔄 iOS: BootstrapSyncCoordinator.smartSync(force=$force)")

            try {
                // Call Swift implementation via callback bridge
                val callbacks = IOSCallbackBridge.getBootstrapSyncCallbacks()
                if (callbacks != null) {
                    println("📞 iOS: Calling Swift smartSync via callback...")
                    // IMPORTANT: Call on Main thread to avoid UI threading issues
                    // Swift callbacks may trigger UI updates which require main thread
                    withContext(Dispatchers.Main) {
                        callbacks.smartSync(force)
                    }
                    println("✅ iOS: Smart sync callback invoked")
                } else {
                    println("⚠️ iOS: Bootstrap sync callbacks not set!")
                }
            } catch (e: Exception) {
                println("❌ iOS: Smart sync failed: ${e.message}")
                e.printStackTrace()
            }
        }

    /**
     * Sync app settings to backend
     *
     * This uploads pending settings changes to the backend server.
     * Called after:
     * - User changes a setting (cloud sync, cellular sync, scan quality)
     * - User taps "Sync Now" button
     * - Network becomes available (after offline changes)
     *
     * TODO: Implement iOS-specific app settings sync
     * For now, this is a placeholder. Settings sync will be added when iOS sync infrastructure is complete.
     */
    actual fun syncAppSettings() {
        println("⚠️ iOS: AppSettings sync not yet implemented")
        println("   Settings will sync on next login or manual sync")
        // TODO: Call Swift AppSettingsSyncManager via callback bridge
        // For now, settings changes are stored locally and will sync on next bootstrap sync
    }

    /**
     * Sync documents to backend and wait for completion
     *
     * iOS implementation: For now, this is a placeholder.
     * Document sync on iOS will be implemented when the document management
     * infrastructure is fully migrated to iOS.
     *
     * @return Result with success (placeholder implementation)
     */
    @HiddenFromObjC
    actual suspend fun syncDocumentsAndWait(): Result<Unit> =
        withContext(Dispatchers.Default) {
            println("⚠️ iOS: Document sync not yet fully implemented")
            println("   Documents will sync on next bootstrap sync")
            // TODO: Implement iOS-specific document sync
            Result.success(Unit)
        }
}
