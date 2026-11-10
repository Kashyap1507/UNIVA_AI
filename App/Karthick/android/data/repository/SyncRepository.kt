package com.kashyap.wizlyai.data.repository

import android.content.Context
import com.kashyap.wizlyai.data.local.WizlyDatabase
import com.kashyap.wizlyai.data.local.entity.*
import com.kashyap.wizlyai.data.local.mapper.EntityMapper
import com.kashyap.wizlyai.network.models.BootstrapData
import com.kashyap.wizlyai.sync.SyncPreferences
import com.kashyap.wizlyai.util.SyncLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Unified repository for all sync operations to Room database
 *
 * This repository consolidates all data syncing logic in one place,
 * replacing the scattered sync implementations across multiple files.
 *
 * Benefits:
 * - Single source of truth for all sync operations
 * - Eliminates ~400 lines of duplicate code
 * - Centralized error handling and logging
 * - Smart sync with timestamp checking (only syncs newer data)
 * - Consistent transaction handling
 * - Easy to test and maintain
 *
 * @param context Android context for database and preferences access
 */
class SyncRepository(context: Context) {
    private val database = WizlyDatabase.getDatabase(context)
    private val syncPrefs = SyncPreferences(context)

    companion object {
        private const val TAG = "SyncRepository"
    }

    /**
     * Sync bootstrap data from the authentication response.
     *
     * Bootstrap carries the app settings and subscription usage; courses,
     * deadlines and cards are fetched by their own repositories.
     *
     * @param bootstrapData Bootstrap data from auth response
     * @param userId Current authenticated user ID
     * @return Result with sync statistics
     */
    suspend fun syncBootstrapData(
        bootstrapData: BootstrapData,
        userId: String,
    ): Result<SyncStats> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                var itemsSynced = 0
                val startTime = System.currentTimeMillis()

                SyncLogger.syncStart(TAG, "Bootstrap data sync for user $userId")
                SyncLogger.detail(TAG, "Sync timestamp: ${bootstrapData.syncTimestamp}")

                // 1. App settings (if provided, otherwise defaults)
                val settingsEntity = EntityMapper.toEntity(bootstrapData.appSettings, userId)
                val settingsSynced = syncAppSettings(settingsEntity)
                if (settingsSynced.isSuccess) {
                    itemsSynced += 1
                    SyncLogger.syncSuccess(TAG, "App settings synced (cloudSync: ${settingsEntity.cloudSyncEnabled})")
                } else {
                    SyncLogger.warning(TAG, "App settings sync failed: ${settingsSynced.exceptionOrNull()?.message}")
                }

                // 2. Subscription usage stats (if provided)
                bootstrapData.subscription?.let { subscriptionData ->
                    val usageStatsEntities = EntityMapper.toUsageStatsEntities(subscriptionData, userId)
                    if (usageStatsEntities.isNotEmpty()) {
                        database.usageStatsDao().insertAll(usageStatsEntities)
                        itemsSynced += usageStatsEntities.size
                        SyncLogger.syncSuccess(TAG, "Synced ${usageStatsEntities.size} usage stats")
                    }
                }

                // 3. Save sync timestamp for future smart sync
                syncPrefs.saveLastSyncTimestamp(bootstrapData.syncTimestamp)

                val duration = System.currentTimeMillis() - startTime
                SyncLogger.stats(
                    TAG,
                    mapOf(
                        "Total items synced" to itemsSynced,
                        "Duration" to "${duration}ms",
                        "Sync timestamp" to bootstrapData.syncTimestamp,
                    ),
                )
                SyncLogger.syncEnd(TAG)

                Result.success(SyncStats(itemsSynced, duration, bootstrapData.syncTimestamp))
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Bootstrap sync failed: ${e.message}", e)
                SyncLogger.syncEnd(TAG)
                Result.failure(e)
            }
        }

    /**
     * Sync the user entity to Room database
     *
     * Called when the profile needs to be persisted locally.
     * Typically called during authentication or profile updates.
     *
     * @param userEntity UserEntity to sync
     * @return Result indicating success or failure
     */
    suspend fun syncUser(userEntity: UserEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                SyncLogger.database(TAG, "Syncing user: ${userEntity.email}")
                database.userDao().insert(userEntity)
                SyncLogger.syncSuccess(TAG, "User synced: ${userEntity.email}")
                Result.success(Unit)
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "User sync failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Sync app settings entity
     *
     * Syncs user app settings to Room database with smart timestamp checking.
     * Settings are per-user and control app behavior like cloud sync for documents.
     *
     * Used during:
     * - Bootstrap sync after authentication (settings from backend)
     * - Settings update from UI (local changes to be uploaded)
     *
     * Smart sync: Only updates if server timestamp > local timestamp
     *
     * @param settingsEntity AppSettingsEntity to sync
     * @return Result indicating success or failure
     */
    suspend fun syncAppSettings(settingsEntity: AppSettingsEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                SyncLogger.database(TAG, "Syncing app settings for user: ${settingsEntity.userId}")

                // Check if local settings exist
                val localSettings = database.appSettingsDao().getByUserId(settingsEntity.userId)

                if (localSettings == null) {
                    // No local settings - insert new
                    database.appSettingsDao().insert(settingsEntity)
                    SyncLogger.syncSuccess(TAG, "App settings inserted (cloudSync: ${settingsEntity.cloudSyncEnabled})")
                } else {
                    // Local settings exist - check timestamp
                    if (shouldSync(settingsEntity.userId, settingsEntity.updatedAt) {
                            database.appSettingsDao().getByUserId(it)?.updatedAt
                        }
                    ) {
                        database.appSettingsDao().update(settingsEntity)
                        SyncLogger.syncSuccess(TAG, "App settings updated (cloudSync: ${settingsEntity.cloudSyncEnabled})")
                    } else {
                        SyncLogger.skipped(TAG, "App settings (local is newer)")
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "App settings sync failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Check if entity should be synced based on timestamp comparison
     *
     * Decision logic:
     * - If no server timestamp → sync (can't determine age)
     * - If no local entity → sync (new entity)
     * - If no local timestamp → sync (data integrity)
     * - If server timestamp > local timestamp → sync (server is newer)
     * - Otherwise → skip (local is same or newer)
     *
     * @param entityId Entity ID to check
     * @param serverUpdatedAt Server's updated_at timestamp (ISO 8601)
     * @param getLocalUpdatedAt Function to retrieve local entity's updated_at
     * @return true if entity should be synced, false otherwise
     */
    private suspend fun shouldSync(
        entityId: String,
        serverUpdatedAt: String?,
        getLocalUpdatedAt: suspend (String) -> String?,
    ): Boolean {
        // If no server timestamp, sync it (can't determine age)
        if (serverUpdatedAt == null || serverUpdatedAt.isEmpty()) {
            return true
        }

        // Get local timestamp
        val localUpdatedAt = getLocalUpdatedAt(entityId)

        // If no local entity, sync it (new entity)
        if (localUpdatedAt == null || localUpdatedAt.isEmpty()) {
            return true
        }

        // Compare timestamps
        return try {
            val serverTime = Instant.parse(serverUpdatedAt)
            val localTime = Instant.parse(localUpdatedAt)
            serverTime >= localTime
        } catch (e: Exception) {
            SyncLogger.warning(TAG, "Failed to parse timestamps for $entityId: ${e.message}")
            // If parsing fails, sync anyway (safer to have data than lose it)
            true
        }
    }

    /**
     * Check if sync is needed based on time elapsed since last sync
     *
     * Uses SyncPreferences to track last sync timestamp.
     * Default interval is 24 hours.
     *
     * @param intervalHours Sync interval in hours (default 24)
     * @return true if sync is needed, false otherwise
     */
    fun shouldSync(intervalHours: Int = 24): Boolean {
        return syncPrefs.shouldSync(intervalHours)
    }

    /**
     * Get last sync timestamp
     *
     * @return Last sync timestamp as ISO 8601 string, or null if never synced
     */
    fun getLastSyncTimestamp(): String? {
        return syncPrefs.getLastSyncTimestamp()
    }

    /**
     * Clear all synced data from database
     *
     * Called during logout to remove all local data.
     * Use with caution - this is destructive!
     *
     * @return Result indicating success or failure
     */
    suspend fun clearSyncState(): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                SyncLogger.syncStart(TAG, "Clearing all sync data")

                // Clear all Room tables
                database.clearAllTables()
                SyncLogger.database(TAG, "All Room tables cleared")

                // Clear sync preferences
                syncPrefs.clearSync()
                SyncLogger.database(TAG, "Sync preferences cleared")

                SyncLogger.syncSuccess(TAG, "Sync state cleared successfully")
                SyncLogger.syncEnd(TAG)
                Result.success(Unit)
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Failed to clear sync state: ${e.message}", e)
                SyncLogger.syncEnd(TAG)
                Result.failure(e)
            }
        }

    /**
     * Get sync statistics: entity counts in the local database.
     */
    suspend fun getSyncStats(): Map<String, Int> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                mapOf(
                    "users" to database.userDao().getCount(),
                    "app_settings" to database.appSettingsDao().getCount(),
                )
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Failed to get sync stats: ${e.message}", e)
                emptyMap()
            }
        }
}

/**
 * Sync statistics data class
 *
 * @param itemsSynced Number of items successfully synced
 * @param durationMs Time taken for sync operation in milliseconds
 * @param syncTimestamp Server sync timestamp from bootstrap data
 */
data class SyncStats(
    val itemsSynced: Int,
    val durationMs: Long,
    val syncTimestamp: String,
)
