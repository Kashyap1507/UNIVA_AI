package com.kashyap.wizlyai.data.local.entity

import androidx.room.*

/**
 * Room entity for app-level user settings
 *
 * Stores user preferences that sync across devices via backend Firestore.
 * Settings are tied to user account and persist through app reinstalls.
 *
 * Features:
 * - Cloud Sync toggle for document image uploads
 * - Extensible for future settings (notifications, themes, etc.)
 * - Cross-device sync via BootstrapData
 * - Offline-first with Room caching
 */
@Entity(
    tableName = "app_settings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE, // Delete settings when user is deleted
        ),
    ],
    indices = [
        Index(value = ["user_id"], unique = true), // One settings record per user
    ],
)
data class AppSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    /**
     * Cloud Sync setting - controls whether document images are uploaded to GCS
     *
     * When TRUE:
     * - Scanned document images uploaded to Google Cloud Storage
     * - Document metadata uploaded to Firestore
     * - Full cross-device access to images
     *
     * When FALSE:
     * - Scanned images remain in local device storage only
     * - Document metadata still uploaded to Firestore (for browsing)
     * - Reduces cloud storage costs and bandwidth usage
     * - Privacy: sensitive documents stay on device
     *
     * Default: false (local-only for privacy and cost savings)
     */
    @ColumnInfo(name = "cloud_sync_enabled")
    val cloudSyncEnabled: Boolean = false,
    /**
     * Cellular Data Sync setting - controls whether to sync over cellular connection
     *
     * When TRUE:
     * - Files will sync over both WiFi and cellular data
     * - May use significant mobile data
     *
     * When FALSE:
     * - Files only sync when connected to WiFi
     * - Saves mobile data usage
     *
     * Note: Only applies when cloudSyncEnabled = true
     * Default: false (WiFi-only to save data)
     */
    @ColumnInfo(name = "cellular_sync_enabled")
    val cellularSyncEnabled: Boolean = false,
    /**
     * Scan Quality setting - determines resolution and file size of scanned documents
     *
     * Values: "SMALL", "STANDARD", "LARGE", "HD"
     * - SMALL: 1024x768, ~0.5 MB - Fast upload, basic quality
     * - STANDARD: 1920x1440, ~1.5 MB - Balanced quality
     * - LARGE: 2592x1936, ~3 MB - High quality (default)
     * - HD: 3264x2448, ~5 MB - Premium quality
     *
     * Default: "LARGE" (high quality)
     */
    @ColumnInfo(name = "scan_quality")
    val scanQuality: String = "LARGE",
    /**
     * Timestamp when settings were created
     */
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    /**
     * Timestamp when settings were last updated
     * Used for smart sync to avoid redundant writes
     */
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    /**
     * Sync status: "synced", "pending", "failed"
     * Tracks whether local changes have been uploaded to backend
     */
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "synced",
) {
    companion object {
        /**
         * Create default settings for a new user
         *
         * @param userId User ID
         * @param timestamp Current timestamp (ISO 8601 format)
         * @return AppSettingsEntity with default values
         */
        fun createDefault(
            userId: String,
            timestamp: String,
        ): AppSettingsEntity {
            return AppSettingsEntity(
                userId = userId,
                cloudSyncEnabled = true, // Default: ON for cloud storage (Firebase Storage)
                cellularSyncEnabled = false, // Default: OFF to save data
                scanQuality = "LARGE", // Default: high quality
                createdAt = timestamp,
                updatedAt = timestamp,
                syncStatus = "synced",
            )
        }
    }
}
