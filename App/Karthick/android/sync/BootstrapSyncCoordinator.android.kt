package com.kashyap.wizlyai.sync

import android.content.Context
import com.kashyap.wizlyai.data.repository.SyncRepository
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.BootstrapData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android implementation of BootstrapSyncCoordinator
 *
 * This is the bridge between the common Auth code and the Android-specific
 * SyncCoordinator/SyncRepository infrastructure.
 */
actual object BootstrapSyncCoordinator {
    // Android context (set by MainActivity or Application class)
    private var context: Context? = null

    // Lazy-initialized SyncCoordinator with DocumentSyncManager and AppSettingsSyncManager
    private val syncCoordinator: SyncCoordinator? by lazy {
        context?.let { ctx ->
            val syncRepository = SyncRepository(ctx)
            val documentSyncManager = DocumentSyncManager(ctx)
            val appSettingsSyncManager = AppSettingsSyncManager(ctx)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            SyncCoordinator(syncRepository, documentSyncManager, appSettingsSyncManager, scope)
        }
    }

    /**
     * Initialize with Android context
     * Call this from MainActivity or Application onCreate
     *
     * @param ctx Android application context
     */
    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        println("✅ BootstrapSyncCoordinator initialized with context")
    }

    /**
     * Sync bootstrap data after authentication
     *
     * Delegates to SyncCoordinator which orchestrates:
     * 1. User entity sync via EntityMapper
     * 2. Bootstrap data sync (app settings, usage) via SyncRepository
     * 3. Smart sync with timestamp checking
     * 4. Cache invalidation
     *
     * @param bootstrapData Bootstrap data from auth response
     * @param user Authenticated user
     */
    actual fun syncBootstrapData(
        bootstrapData: BootstrapData,
        user: AuthUser,
    ) {
        val coordinator = syncCoordinator

        if (coordinator == null) {
            println("⚠️  BootstrapSyncCoordinator not initialized! Call initialize(context) first.")
            println("   Sync will be skipped. This should only happen in tests.")
            return
        }

        // Delegate to SyncCoordinator
        coordinator.syncBootstrapData(bootstrapData, user)
    }

    /**
     * Clear all synced data (called on logout)
     *
     * Clears:
     * - All Room database tables
     * - All sync preferences (timestamps)
     * - All caches
     */
    actual suspend fun clearSyncData() {
        val coordinator = syncCoordinator

        if (coordinator == null) {
            println("⚠️  BootstrapSyncCoordinator not initialized!")
            return
        }

        coordinator.clearSyncData()
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
     * Now also triggers:
     * - Document sync to upload pending documents/pages
     * - Settings sync to upload pending settings changes
     *
     * @param force Force sync even if not needed based on time interval
     */
    actual suspend fun smartSync(force: Boolean) {
        val coordinator = syncCoordinator

        if (coordinator == null) {
            println("⚠️  BootstrapSyncCoordinator not initialized!")
            return
        }

        // Delegate to SyncCoordinator's smart sync (bootstrap data)
        coordinator.smartSync(force)

        // Also sync documents (upload pending documents/pages)
        coordinator.syncDocuments()

        // Also sync app settings (upload pending changes)
        coordinator.syncAppSettings()
    }

    /**
     * Sync documents to backend
     *
     * This is a convenience method for triggering document sync
     * independently of bootstrap sync.
     *
     * Use cases:
     * - After scanning a new document
     * - After adding pages to existing document
     * - When network becomes available
     * - Manual sync trigger from UI
     */
    fun syncDocuments() {
        val coordinator = syncCoordinator

        if (coordinator == null) {
            println("⚠️  BootstrapSyncCoordinator not initialized!")
            return
        }

        coordinator.syncDocuments()
    }

    /**
     * Sync app settings to backend
     *
     * This is a convenience method for triggering settings sync
     * independently of bootstrap sync.
     *
     * Use cases:
     * - After changing a setting (cloud sync, cellular sync, scan quality)
     * - When user taps "Sync Now" button
     * - When network becomes available after offline changes
     * - Manual sync trigger from UI
     */
    actual fun syncAppSettings() {
        val coordinator = syncCoordinator

        if (coordinator == null) {
            println("⚠️  BootstrapSyncCoordinator not initialized!")
            return
        }

        coordinator.syncAppSettings()
    }

    /**
     * Sync documents to backend and wait for completion
     *
     * This is a suspend function that ensures documents are uploaded to the backend
     * before returning. Useful when you need to guarantee the document exists on the
     * server before calling other APIs (e.g., "Check my work").
     *
     * @return Result with sync stats or error
     */
    actual suspend fun syncDocumentsAndWait(): Result<Unit> {
        val coordinator = syncCoordinator

        if (coordinator == null) {
            println("⚠️  BootstrapSyncCoordinator not initialized!")
            return Result.failure(Exception("BootstrapSyncCoordinator not initialized"))
        }

        return try {
            kotlinx.coroutines.suspendCancellableCoroutine<Result<Unit>> { continuation ->
                coordinator.syncDocuments { result ->
                    println("📊 Document sync callback received: ${if (result.isSuccess) "SUCCESS" else "FAILURE"}")

                    // Map Result<DocumentSyncStats> to Result<Unit>
                    val mappedResult = result.map { Unit }
                    continuation.resume(mappedResult) { }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
