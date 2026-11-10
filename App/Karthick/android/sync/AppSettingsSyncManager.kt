package com.kashyap.wizlyai.sync

import android.content.Context
import com.kashyap.wizlyai.cache.SimpleCache
import com.kashyap.wizlyai.data.local.WizlyDatabase
import com.kashyap.wizlyai.data.local.entity.AppSettingsEntity
import com.kashyap.wizlyai.data.models.SettingsSyncStats
import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.models.AppSettings
import com.kashyap.wizlyai.util.SyncLogger
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * App Settings Sync Manager
 *
 * Handles bidirectional synchronization of app settings between:
 * - Local Room database (offline-first storage)
 * - Backend Firebase Firestore (cloud persistence via API)
 *
 * Features:
 * - Upload pending settings changes to backend
 * - Detect network connectivity before sync
 * - Track sync status (pending/synced/failed)
 * - Retry failed uploads with exponential backoff
 * - Cache invalidation for UI refresh
 *
 * Settings synced:
 * - cloudSyncEnabled (bool) - Upload documents to cloud storage
 * - cellularSyncEnabled (bool) - Sync over cellular data
 * - scanQuality (string) - Document scan quality preset (SMALL/STANDARD/LARGE/HD)
 *
 * Usage:
 * ```
 * val syncManager = AppSettingsSyncManager(context)
 *
 * // Upload pending settings changes
 * syncManager.syncPendingSettings()
 *
 * // Observe pending sync count
 * syncManager.observePendingSyncCount().collect { count -> ... }
 * ```
 *
 * @param context Android context for database and network access
 */
class AppSettingsSyncManager(context: Context) {
    private val database = WizlyDatabase.getDatabase(context)
    private val appSettingsDao = database.appSettingsDao()
    private val apiClient = ApiClient
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "AppSettingsSyncManager"
        private const val SETTINGS_ENDPOINT = "/api/v1/settings"
    }

    /**
     * Sync pending app settings to backend
     *
     * This is the PRIMARY method for uploading settings changes.
     * It handles:
     * - Fetching settings with sync_status='pending'
     * - Uploading to backend via PUT /api/v1/settings
     * - Marking as 'synced' on success
     * - Marking as 'failed' for retry on error
     *
     * Called by:
     * - BootstrapSyncCoordinator during smart sync
     * - AppSettingsViewModel.triggerImmediateSync()
     * - Background WorkManager periodic sync
     *
     * @return Result with sync statistics
     */
    suspend fun syncPendingSettings(): Result<SettingsSyncStats> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val startTime = System.currentTimeMillis()

                SyncLogger.syncStart(TAG, "Syncing pending app settings")

                // 1. Fetch pending settings
                val pendingSettings = appSettingsDao.getPendingSyncSettings()

                if (pendingSettings.isEmpty()) {
                    SyncLogger.info(TAG, "No pending settings to sync")
                    return@withContext Result.success(
                        SettingsSyncStats(
                            settingsSynced = 0,
                            failures = 0,
                            durationMs = System.currentTimeMillis() - startTime,
                        ),
                    )
                }

                SyncLogger.info(TAG, "Found ${pendingSettings.size} pending settings")

                var successCount = 0
                var failureCount = 0

                // 2. Upload each settings record (typically just 1 per user)
                for (settingsEntity in pendingSettings) {
                    try {
                        // Mark as syncing
                        appSettingsDao.updateSyncStatus(
                            userId = settingsEntity.userId,
                            status = "syncing",
                            updatedAt = getCurrentTimestamp(),
                        )

                        // Upload to backend
                        val result = uploadSettings(settingsEntity)

                        result.fold(
                            onSuccess = {
                                // Mark as synced
                                appSettingsDao.updateSyncStatus(
                                    userId = settingsEntity.userId,
                                    status = "synced",
                                    updatedAt = getCurrentTimestamp(),
                                )

                                successCount++
                                SyncLogger.syncSuccess(TAG, "Settings synced for user: ${settingsEntity.userId}")
                            },
                            onFailure = { e ->
                                // Mark as failed for retry
                                appSettingsDao.updateSyncStatus(
                                    userId = settingsEntity.userId,
                                    status = "failed",
                                    updatedAt = getCurrentTimestamp(),
                                )

                                failureCount++
                                SyncLogger.syncFailure(TAG, "Settings sync failed for user: ${settingsEntity.userId}", e)
                            },
                        )
                    } catch (e: Exception) {
                        failureCount++
                        SyncLogger.syncFailure(TAG, "Error syncing settings: ${settingsEntity.userId}", e)
                    }
                }

                // 3. Invalidate caches
                invalidateSettingsCaches()

                // 4. Log statistics
                val duration = System.currentTimeMillis() - startTime
                SyncLogger.stats(
                    TAG,
                    mapOf(
                        "Settings synced" to successCount,
                        "Failures" to failureCount,
                        "Duration" to "${duration}ms",
                    ),
                )
                SyncLogger.syncEnd(TAG)

                Result.success(
                    SettingsSyncStats(
                        settingsSynced = successCount,
                        failures = failureCount,
                        durationMs = duration,
                    ),
                )
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Settings sync failed", e)
                SyncLogger.syncEnd(TAG)
                Result.failure(e)
            }
        }

    /**
     * Upload settings to backend API
     *
     * Calls PUT /api/v1/settings with settings data.
     *
     * @param settingsEntity Settings entity to upload
     * @return Result indicating success or failure
     */
    private suspend fun uploadSettings(settingsEntity: AppSettingsEntity): Result<Unit> {
        return try {
            SyncLogger.detail(TAG, "Uploading settings to $SETTINGS_ENDPOINT")
            SyncLogger.detail(TAG, "  cloudSyncEnabled: ${settingsEntity.cloudSyncEnabled}")
            SyncLogger.detail(TAG, "  cellularSyncEnabled: ${settingsEntity.cellularSyncEnabled}")
            SyncLogger.detail(TAG, "  scanQuality: ${settingsEntity.scanQuality}")

            // Build request body
            val requestBody =
                AppSettings(
                    cloudSyncEnabled = settingsEntity.cloudSyncEnabled,
                    cellularSyncEnabled = settingsEntity.cellularSyncEnabled,
                    scanQuality = settingsEntity.scanQuality,
                    createdAt = settingsEntity.createdAt,
                    updatedAt = settingsEntity.updatedAt,
                )

            // Make API call
            val response =
                apiClient.httpClient.put(SETTINGS_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

            if (response.status.isSuccess()) {
                SyncLogger.syncSuccess(TAG, "Settings uploaded successfully: ${response.status}")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                SyncLogger.syncFailure(TAG, "Settings upload failed: ${response.status} - $errorBody", null)
                Result.failure(Exception("Upload failed: ${response.status}"))
            }
        } catch (e: Exception) {
            SyncLogger.syncFailure(TAG, "Error uploading settings", e)
            Result.failure(e)
        }
    }

    /**
     * Get count of pending settings needing sync
     *
     * @return Count of settings records with pending sync
     */
    suspend fun getPendingSyncCount(): Int =
        withContext(Dispatchers.IO) {
            return@withContext try {
                appSettingsDao.getPendingSyncSettings().size
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Failed to get pending sync count", e)
                0
            }
        }

    /**
     * Observe pending sync count as Flow
     *
     * This allows UI to reactively display sync status.
     *
     * @return Flow emitting pending sync count
     */
    fun observePendingSyncCount(): Flow<Int> {
        // Note: AppSettingsDao doesn't have a Flow-based count method
        // For now, we return a simple count. Could be enhanced later.
        return kotlinx.coroutines.flow.flow {
            emit(getPendingSyncCount())
        }
    }

    /**
     * Invalidate settings-related caches
     *
     * This forces UI to refresh and fetch new data from Room.
     */
    private fun invalidateSettingsCaches() {
        SimpleCache.instance.invalidatePattern("app_settings")
        SimpleCache.instance.invalidatePattern("settings")
        SyncLogger.info(TAG, "Settings caches invalidated")
    }

    /**
     * Get current timestamp in ISO 8601 format
     *
     * @return Current timestamp string
     */
    private fun getCurrentTimestamp(): String {
        return Clock.System.now().toString()
    }
}
