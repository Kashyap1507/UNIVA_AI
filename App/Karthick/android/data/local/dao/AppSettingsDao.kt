package com.kashyap.wizlyai.data.local.dao

import androidx.room.*
import com.kashyap.wizlyai.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for app settings operations
 *
 * Provides CRUD operations and reactive Flow-based queries for app settings.
 * Settings are per-user and sync with backend Firestore.
 */
@Dao
interface AppSettingsDao {
    /**
     * Insert or replace app settings for a user
     *
     * Uses REPLACE strategy to ensure one settings record per user.
     * Called during:
     * - Bootstrap sync after login
     * - Settings update from UI
     * - Backend sync when settings change on another device
     *
     * @param settings AppSettingsEntity to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: AppSettingsEntity): Long

    /**
     * Update existing app settings
     *
     * @param settings AppSettingsEntity with updated values
     */
    @Update
    suspend fun update(settings: AppSettingsEntity)

    /**
     * Delete app settings for a user
     *
     * Typically called during logout or account deletion.
     * Foreign key CASCADE will auto-delete when user is deleted.
     *
     * @param settings AppSettingsEntity to delete
     */
    @Delete
    suspend fun delete(settings: AppSettingsEntity)

    /**
     * Get app settings for a user (one-shot query)
     *
     * Returns null if user has no settings yet (new user).
     * UI should create default settings in this case.
     *
     * @param userId User ID
     * @return AppSettingsEntity or null if not found
     */
    @Query("SELECT * FROM app_settings WHERE user_id = :userId")
    suspend fun getByUserId(userId: String): AppSettingsEntity?

    /**
     * Get app settings as reactive Flow
     *
     * Emits new values whenever settings change in database.
     * Perfect for UI observation and real-time updates.
     *
     * Usage:
     * ```
     * appSettingsDao.getByUserIdFlow(userId)
     *     .collect { settings ->
     *         cloudSyncEnabled = settings?.cloudSyncEnabled ?: false
     *     }
     * ```
     *
     * @param userId User ID
     * @return Flow<AppSettingsEntity?> emitting settings changes
     */
    @Query("SELECT * FROM app_settings WHERE user_id = :userId")
    fun getByUserIdFlow(userId: String): Flow<AppSettingsEntity?>

    /**
     * Update cloud sync enabled flag
     *
     * Convenience method for toggling the most common setting.
     * Also updates timestamp and sync status.
     *
     * @param userId User ID
     * @param enabled New cloud sync state
     * @param updatedAt Current timestamp (ISO 8601)
     * @param syncStatus New sync status ("pending" to trigger upload)
     */
    @Query(
        """
        UPDATE app_settings
        SET cloud_sync_enabled = :enabled,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE user_id = :userId
    """,
    )
    suspend fun updateCloudSyncEnabled(
        userId: String,
        enabled: Boolean,
        updatedAt: String,
        syncStatus: String = "pending",
    )

    /**
     * Update cellular sync enabled flag
     *
     * Controls whether files sync over cellular data or WiFi only.
     * Only applies when cloudSyncEnabled = true.
     *
     * @param userId User ID
     * @param enabled New cellular sync state
     * @param updatedAt Current timestamp (ISO 8601)
     * @param syncStatus New sync status ("pending" to trigger upload)
     */
    @Query(
        """
        UPDATE app_settings
        SET cellular_sync_enabled = :enabled,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE user_id = :userId
    """,
    )
    suspend fun updateCellularSyncEnabled(
        userId: String,
        enabled: Boolean,
        updatedAt: String,
        syncStatus: String = "pending",
    )

    /**
     * Update scan quality setting
     *
     * Changes the resolution preset for document scanning.
     * Valid values: "SMALL", "STANDARD", "LARGE", "HD"
     *
     * @param userId User ID
     * @param quality New scan quality preset
     * @param updatedAt Current timestamp (ISO 8601)
     * @param syncStatus New sync status ("pending" to trigger upload)
     */
    @Query(
        """
        UPDATE app_settings
        SET scan_quality = :quality,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE user_id = :userId
    """,
    )
    suspend fun updateScanQuality(
        userId: String,
        quality: String,
        updatedAt: String,
        syncStatus: String = "pending",
    )

    /**
     * Get settings with pending sync status
     *
     * Used by sync worker to find settings that need upload to backend.
     *
     * @return List of settings needing sync
     */
    @Query("SELECT * FROM app_settings WHERE sync_status = 'pending'")
    suspend fun getPendingSyncSettings(): List<AppSettingsEntity>

    /**
     * Update sync status after backend sync
     *
     * Called by SyncRepository after successful upload to Firestore.
     *
     * @param userId User ID
     * @param status New sync status ("synced", "failed")
     * @param updatedAt Current timestamp
     */
    @Query(
        """
        UPDATE app_settings
        SET sync_status = :status,
            updated_at = :updatedAt
        WHERE user_id = :userId
    """,
    )
    suspend fun updateSyncStatus(
        userId: String,
        status: String,
        updatedAt: String,
    )

    /**
     * Delete all app settings
     *
     * Used during logout or data clear operations.
     */
    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()

    /**
     * Get count of app settings records
     *
     * Used for debugging and testing.
     * Should typically be 0 (logged out) or 1 (logged in with settings).
     *
     * @return Number of settings records in database
     */
    @Query("SELECT COUNT(*) FROM app_settings")
    suspend fun getCount(): Int
}
