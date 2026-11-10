package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.cache.SimpleCache
import com.kashyap.wizlyai.data.local.mapper.EntityMapper
import com.kashyap.wizlyai.data.models.SettingsSyncStats
import com.kashyap.wizlyai.data.repository.SyncRepository
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.BootstrapData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Coordinates all sync operations in the app
 *
 * This coordinator acts as a single entry point for all sync-related operations,
 * orchestrating data flow from authentication → sync → cache invalidation.
 *
 * Benefits:
 * - Single responsibility: orchestration only, no business logic
 * - Coordinates between SyncRepository (data layer) and cache (presentation layer)
 * - Clean separation of concerns from AuthViewModel
 * - Easy to test in isolation
 * - Handles sync errors gracefully
 * - Future-proof for incremental sync and conflict resolution
 *
 * Replaces:
 * - BootstrapSync (expect/actual pattern) - direct sync calls
 * - Part of UserSessionManager - sync orchestration logic
 * - Scattered cache invalidation calls
 *
 * @param syncRepository SyncRepository for database operations
 * @param documentSyncManager DocumentSyncManager for document sync operations
 * @param appSettingsSyncManager AppSettingsSyncManager for settings sync operations
 * @param scope CoroutineScope for launching async operations
 */
class SyncCoordinator(
    private val syncRepository: SyncRepository,
    private val documentSyncManager: DocumentSyncManager?,
    private val appSettingsSyncManager: AppSettingsSyncManager?,
    private val scope: CoroutineScope,
) {
    private companion object {
        private const val TAG = "SyncCoordinator"
    }

    /**
     * Sync bootstrap data after authentication
     *
     * This is the MAIN entry point for syncing after login/signup.
     * It orchestrates:
     * 1. User entity sync
     * 2. Bootstrap data sync (app settings, usage)
     * 3. Cache invalidation (force UI refresh)
     *
     * Called by AuthViewModel after successful authentication.
     *
     * @param bootstrapData Bootstrap data from auth response
     * @param user Authenticated user
     * @param onComplete Optional callback when sync completes (for UI feedback)
     */
    fun syncBootstrapData(
        bootstrapData: BootstrapData,
        user: AuthUser,
        onComplete: ((Result<SyncStats>) -> Unit)? = null,
    ) {
        scope.launch {
            println("═══════════════════════════════════════════════════════")
            println("🔄 SyncCoordinator: Starting bootstrap sync")
            println("═══════════════════════════════════════════════════════")
            println("👤 User: ${user.email} (ID: ${user.id})")
            println("   - Sync timestamp: ${bootstrapData.syncTimestamp}")

            try {
                // 1. Sync user entity first
                println("")
                println("📝 Step 1: Syncing user entity...")
                val userEntity = EntityMapper.toEntity(user)
                val userResult = syncRepository.syncUser(userEntity)

                if (userResult.isFailure) {
                    val error = userResult.exceptionOrNull()
                    println("   ❌ User sync failed: ${error?.message}")
                    println("═══════════════════════════════════════════════════════")
                    println("❌ SyncCoordinator: Bootstrap sync FAILED (user sync)")
                    println("═══════════════════════════════════════════════════════")

                    // Notify callback and STOP sync process
                    onComplete?.invoke(Result.failure(error ?: Exception("User sync failed")))
                    return@launch
                }

                println("   ✅ User entity synced successfully")

                // 2. Sync bootstrap data (app settings, usage)
                println("")
                println("📝 Step 2: Syncing bootstrap data...")
                val bootstrapResult = syncRepository.syncBootstrapData(bootstrapData, user.id)

                bootstrapResult.fold(
                    onSuccess = { stats ->
                        println("   ✅ Bootstrap sync successful!")
                        println("   📊 Items synced: ${stats.itemsSynced}")
                        println("   ⏱️  Duration: ${stats.durationMs}ms")

                        // 3. Invalidate caches to force UI refresh with new data
                        println("")
                        println("📝 Step 3: Invalidating caches...")
                        invalidateCaches()
                        println("   ✅ Caches invalidated - UI will refresh")

                        println("═══════════════════════════════════════════════════════")
                        println("✅ SyncCoordinator: Bootstrap sync COMPLETE")
                        println("═══════════════════════════════════════════════════════")

                        // Notify callback
                        onComplete?.invoke(Result.success(stats))
                    },
                    onFailure = { e ->
                        println("   ❌ Bootstrap sync failed: ${e.message}")
                        println("═══════════════════════════════════════════════════════")
                        println("❌ SyncCoordinator: Bootstrap sync FAILED")
                        println("═══════════════════════════════════════════════════════")

                        // Notify callback
                        onComplete?.invoke(Result.failure(e))

                        // TODO: Add retry logic or queue for later sync
                        // For now, sync failure is logged but app continues
                    },
                )
            } catch (e: Exception) {
                println("❌ SyncCoordinator: Unexpected error during sync")
                println("   Error: ${e.message}")
                e.printStackTrace()
                println("═══════════════════════════════════════════════════════")

                // Notify callback
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    /**
     * Trigger smart sync to check for updates
     *
     * This can be called:
     * - Periodically (e.g., every 24 hours)
     * - When app comes to foreground
     * - When user explicitly requests refresh
     * - In response to FCM push notification
     *
     * Smart sync only fetches data that changed since last sync,
     * reducing network bandwidth and improving performance.
     *
     * @param force Force sync even if not needed based on time interval
     */
    suspend fun smartSync(force: Boolean = false) {
        if (!force && !syncRepository.shouldSync()) {
            println("⏭️  SyncCoordinator: Smart sync skipped (recent sync)")
            println("   Last sync: ${syncRepository.getLastSyncTimestamp()}")
            return
        }

        println("🔄 SyncCoordinator: Smart sync triggered")

        // TODO: Implement incremental sync
        // This requires backend support for /api/v1/sync/incremental endpoint
        // Algorithm:
        // 1. Get lastSyncTimestamp from SyncPreferences
        // 2. Call backend /sync/incremental?since={timestamp}
        // 3. Backend returns only entities updated since timestamp
        // 4. Sync only changed entities (much faster than full sync)
        // 5. Update lastSyncTimestamp

        println("⚠️  Incremental sync not yet implemented - requires backend support")
        println("   Placeholder for future incremental sync feature")

        // For now, invalidate caches to force refresh from API
        invalidateCaches()
    }

    /**
     * Invalidate caches after sync
     *
     * This forces the UI to refresh and fetch new data from Room database.
     * Called automatically after successful sync operations.
     *
     */
    private fun invalidateCaches() {
        SimpleCache.instance.invalidatePattern("documents")
        SimpleCache.instance.invalidatePattern("courses")
        println("🧹 Caches invalidated: documents, courses")
    }

    /**
     * Clear all synced data (logout)
     *
     * Clears:
     * - All Room database tables
     * - All sync preferences (timestamps)
     * - All caches
     *
     * Called during logout to ensure clean state.
     *
     * @return Result indicating success or failure
     */
    suspend fun clearSyncData(): Result<Unit> {
        println("═══════════════════════════════════════════════════════")
        println("🧹 SyncCoordinator: Clearing all sync data")
        println("═══════════════════════════════════════════════════════")

        return try {
            // Clear Room database and sync preferences
            val result = syncRepository.clearSyncState()

            result.fold(
                onSuccess = {
                    // Clear all caches
                    SimpleCache.instance.clear()
                    println("✅ All sync data cleared successfully")
                    println("═══════════════════════════════════════════════════════")
                },
                onFailure = { e ->
                    println("❌ Failed to clear sync data: ${e.message}")
                    println("═══════════════════════════════════════════════════════")
                },
            )

            result
        } catch (e: Exception) {
            println("❌ Error clearing sync data: ${e.message}")
            println("═══════════════════════════════════════════════════════")
            Result.failure(e)
        }
    }

    /**
     * Get sync statistics
     *
     * Provides overview of synced data for debugging/monitoring.
     *
     * @return Map of entity counts
     */
    suspend fun getSyncStats(): Map<String, Int> {
        return syncRepository.getSyncStats()
    }

    /**
     * Check if sync is needed based on time interval
     *
     * @param intervalHours Sync interval in hours (default 24)
     * @return true if sync is needed
     */
    fun shouldSync(intervalHours: Int = 24): Boolean {
        return syncRepository.shouldSync(intervalHours)
    }

    /**
     * Sync app settings to backend
     *
     * This uploads pending settings changes to the backend server.
     * Called after:
     * - User changes a setting (cloud sync, cellular sync, scan quality)
     * - User taps "Sync Now" button
     * - Network becomes available (after offline changes)
     * - Periodic background sync
     *
     * @param onComplete Optional callback when sync completes
     */
    fun syncAppSettings(onComplete: ((Result<SettingsSyncStats>) -> Unit)? = null) {
        if (appSettingsSyncManager == null) {
            println("⚠️  AppSettingsSyncManager not initialized, skipping settings sync")
            return
        }

        scope.launch {
            println("═══════════════════════════════════════════════════════")
            println("⚙️  SyncCoordinator: Starting app settings sync")
            println("═══════════════════════════════════════════════════════")

            try {
                val result = appSettingsSyncManager.syncPendingSettings()

                result.fold(
                    onSuccess = { stats ->
                        println("   ✅ Settings synced: ${stats.settingsSynced}")
                        if (stats.failures > 0) {
                            println("   ⚠️  Failures: ${stats.failures}")
                        }

                        println("═══════════════════════════════════════════════════════")
                        println("✅ SyncCoordinator: App settings sync COMPLETE")
                        println("   ⏱️  Duration: ${stats.durationMs}ms")
                        println("═══════════════════════════════════════════════════════")

                        onComplete?.invoke(Result.success(stats))
                    },
                    onFailure = { e ->
                        println("   ❌ Settings sync failed: ${e.message}")
                        println("═══════════════════════════════════════════════════════")
                        println("❌ SyncCoordinator: App settings sync FAILED")
                        println("═══════════════════════════════════════════════════════")

                        onComplete?.invoke(Result.failure(e))
                    },
                )
            } catch (e: Exception) {
                println("❌ SyncCoordinator: Settings sync FAILED")
                println("   Error: ${e.message}")
                e.printStackTrace()
                println("═══════════════════════════════════════════════════════")

                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    /**
     * Sync documents and pages to backend
     *
     * This uploads pending documents and pages to the backend server.
     * Called after:
     * - User scans a new document
     * - User adds pages to existing document
     * - Network becomes available (after offline mode)
     * - Periodic background sync
     *
     * @param onComplete Optional callback when sync completes
     */
    fun syncDocuments(onComplete: ((Result<DocumentSyncStats>) -> Unit)? = null) {
        if (documentSyncManager == null) {
            println("⚠️  DocumentSyncManager not initialized, skipping document sync")
            return
        }

        scope.launch {
            println("═══════════════════════════════════════════════════════")
            println("📄 SyncCoordinator: Starting document sync")
            println("═══════════════════════════════════════════════════════")

            try {
                // 1. Sync pending documents
                println("")
                println("📝 Step 1: Syncing pending documents...")
                val documentResult = documentSyncManager.syncPendingDocumentsInternal()

                documentResult.fold(
                    onSuccess = { stats ->
                        println("   ✅ Documents synced: ${stats.documentsSynced}")
                        println("   📄 Pages synced: ${stats.pagesSynced}")
                        if (stats.failures > 0) {
                            println("   ⚠️  Failures: ${stats.failures}")
                        }
                    },
                    onFailure = { e ->
                        println("   ❌ Document sync failed: ${e.message}")
                    },
                )

                // 2. Sync pending pages
                println("")
                println("📝 Step 2: Syncing pending pages...")
                val pageResult = documentSyncManager.syncPendingPagesInternal()

                pageResult.fold(
                    onSuccess = { stats ->
                        println("   ✅ Pages synced: ${stats.pagesSynced}")
                        if (stats.failures > 0) {
                            println("   ⚠️  Failures: ${stats.failures}")
                        }
                    },
                    onFailure = { e ->
                        println("   ❌ Page sync failed: ${e.message}")
                    },
                )

                // 3. Combine results
                val combinedStats =
                    DocumentSyncStats(
                        documentsSynced = documentResult.getOrNull()?.documentsSynced ?: 0,
                        pagesSynced =
                            (documentResult.getOrNull()?.pagesSynced ?: 0) +
                                (pageResult.getOrNull()?.pagesSynced ?: 0),
                        failures =
                            (documentResult.getOrNull()?.failures ?: 0) +
                                (pageResult.getOrNull()?.failures ?: 0),
                        durationMs =
                            (documentResult.getOrNull()?.durationMs ?: 0) +
                                (pageResult.getOrNull()?.durationMs ?: 0),
                    )

                println("═══════════════════════════════════════════════════════")
                println("✅ SyncCoordinator: Document sync COMPLETE")
                println("   📊 Total synced: ${combinedStats.documentsSynced} docs, ${combinedStats.pagesSynced} pages")
                println("   ⏱️  Duration: ${combinedStats.durationMs}ms")
                println("═══════════════════════════════════════════════════════")

                onComplete?.invoke(Result.success(combinedStats))
            } catch (e: Exception) {
                println("❌ SyncCoordinator: Document sync FAILED")
                println("   Error: ${e.message}")
                e.printStackTrace()
                println("═══════════════════════════════════════════════════════")

                onComplete?.invoke(Result.failure(e))
            }
        }
    }
}

/**
 * Sync statistics data class (re-exported for convenience)
 */
typealias SyncStats = com.kashyap.wizlyai.data.repository.SyncStats
