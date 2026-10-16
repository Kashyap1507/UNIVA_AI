package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.models.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import platform.Foundation.NSUserDefaults

/**
 * Platform-specific factory for iOS
 */
actual fun createAppSettingsRepository(platformContext: Any?): AppSettingsRepository {
    return AppSettingsRepositoryImpl()
}

/**
 * iOS implementation of AppSettingsRepository using NSUserDefaults
 *
 * Persists app settings to iOS UserDefaults storage for permanent storage.
 * Keys match IOSAppSettingsSyncManager.swift for compatibility with backend sync.
 *
 * @see IOSAppSettingsSyncManager.swift for sync integration
 */
private class AppSettingsRepositoryImpl : AppSettingsRepository {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    // NSUserDefaults keys (must match IOSAppSettingsSyncManager.swift)
    private companion object {
        const val KEY_CLOUD_SYNC = "cloud_sync_enabled"
        const val KEY_CELLULAR_SYNC = "cellular_sync_enabled"
        const val KEY_SCAN_QUALITY = "scan_quality"
        const val KEY_CREATED_AT = "app_settings_created_at"
        const val KEY_UPDATED_AT = "app_settings_updated_at"
        const val KEY_SYNC_STATUS = "app_settings_sync_status"
    }

    override suspend fun getSettings(userId: String): Result<AppSettings> {
        return try {
            val hasSettings = userDefaults.objectForKey(KEY_CREATED_AT) != null

            if (!hasSettings) {
                // Return defaults for new user (not persisted yet)
                // Default cloudSync to true to match backend/Android defaults
                val timestamp = Clock.System.now().toString()
                println("📱 iOS AppSettings: No settings found, returning defaults for user $userId")
                Result.success(
                    AppSettings(
                        cloudSyncEnabled = true, // Default: ON for cloud storage (Firebase Storage)
                        cellularSyncEnabled = false,
                        scanQuality = "LARGE",
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
            } else {
                // Read from UserDefaults
                val cloudSync = userDefaults.boolForKey(KEY_CLOUD_SYNC)
                val cellularSync = userDefaults.boolForKey(KEY_CELLULAR_SYNC)
                val scanQuality = userDefaults.stringForKey(KEY_SCAN_QUALITY) ?: "LARGE"
                val createdAt = userDefaults.stringForKey(KEY_CREATED_AT)
                val updatedAt = userDefaults.stringForKey(KEY_UPDATED_AT)

                println("📱 iOS AppSettings: Loaded settings for user $userId")
                println("   Cloud Sync: $cloudSync, Cellular: $cellularSync, Quality: $scanQuality")

                Result.success(
                    AppSettings(
                        cloudSyncEnabled = cloudSync,
                        cellularSyncEnabled = cellularSync,
                        scanQuality = scanQuality,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                )
            }
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error loading settings: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getSettingsFlow(userId: String): Flow<AppSettings?> {
        // Simple flow implementation - emits current value
        // Note: For reactive updates, would need KVO observer on NSUserDefaults
        return try {
            val hasSettings = userDefaults.objectForKey(KEY_CREATED_AT) != null

            if (!hasSettings) {
                // Return null flow for new users (no settings yet)
                flowOf(null)
            } else {
                val cloudSync = userDefaults.boolForKey(KEY_CLOUD_SYNC)
                val cellularSync = userDefaults.boolForKey(KEY_CELLULAR_SYNC)
                val scanQuality = userDefaults.stringForKey(KEY_SCAN_QUALITY) ?: "LARGE"
                val createdAt = userDefaults.stringForKey(KEY_CREATED_AT)
                val updatedAt = userDefaults.stringForKey(KEY_UPDATED_AT)

                flowOf(
                    AppSettings(
                        cloudSyncEnabled = cloudSync,
                        cellularSyncEnabled = cellularSync,
                        scanQuality = scanQuality,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    ),
                )
            }
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error in settings flow: ${e.message}")
            flowOf(null)
        }
    }

    override suspend fun updateCloudSync(
        userId: String,
        enabled: Boolean,
    ): Result<AppSettings> {
        return try {
            val timestamp = Clock.System.now().toString()

            // Initialize createdAt if first time
            if (userDefaults.objectForKey(KEY_CREATED_AT) == null) {
                userDefaults.setObject(timestamp, forKey = KEY_CREATED_AT)
                println("📱 iOS AppSettings: Initialized settings for user $userId")
            }

            // Update cloud sync setting
            userDefaults.setBool(enabled, forKey = KEY_CLOUD_SYNC)
            userDefaults.setObject(timestamp, forKey = KEY_UPDATED_AT)
            userDefaults.setObject("pending", forKey = KEY_SYNC_STATUS)
            userDefaults.synchronize()

            println("✅ iOS AppSettings: Updated cloud sync to $enabled for user $userId")

            // Return updated settings
            getSettings(userId)
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error updating cloud sync: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateCellularSync(
        userId: String,
        enabled: Boolean,
    ): Result<AppSettings> {
        return try {
            val timestamp = Clock.System.now().toString()

            // Initialize createdAt if first time
            if (userDefaults.objectForKey(KEY_CREATED_AT) == null) {
                userDefaults.setObject(timestamp, forKey = KEY_CREATED_AT)
                println("📱 iOS AppSettings: Initialized settings for user $userId")
            }

            // Update cellular sync setting
            userDefaults.setBool(enabled, forKey = KEY_CELLULAR_SYNC)
            userDefaults.setObject(timestamp, forKey = KEY_UPDATED_AT)
            userDefaults.setObject("pending", forKey = KEY_SYNC_STATUS)
            userDefaults.synchronize()

            println("✅ iOS AppSettings: Updated cellular sync to $enabled for user $userId")

            // Return updated settings
            getSettings(userId)
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error updating cellular sync: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateScanQuality(
        userId: String,
        quality: String,
    ): Result<AppSettings> {
        return try {
            val timestamp = Clock.System.now().toString()

            // Validate quality value
            val validQualities = setOf("SMALL", "STANDARD", "LARGE", "HD")
            if (quality !in validQualities) {
                println("⚠️ iOS AppSettings: Invalid quality '$quality', using LARGE")
                return updateScanQuality(userId, "LARGE")
            }

            // Initialize createdAt if first time
            if (userDefaults.objectForKey(KEY_CREATED_AT) == null) {
                userDefaults.setObject(timestamp, forKey = KEY_CREATED_AT)
                println("📱 iOS AppSettings: Initialized settings for user $userId")
            }

            // Update scan quality setting
            userDefaults.setObject(quality, forKey = KEY_SCAN_QUALITY)
            userDefaults.setObject(timestamp, forKey = KEY_UPDATED_AT)
            userDefaults.setObject("pending", forKey = KEY_SYNC_STATUS)
            userDefaults.synchronize()

            println("✅ iOS AppSettings: Updated scan quality to $quality for user $userId")

            // Return updated settings
            getSettings(userId)
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error updating scan quality: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateSettings(
        userId: String,
        settings: AppSettings,
    ): Result<AppSettings> {
        return try {
            val timestamp = Clock.System.now().toString()

            // Use provided timestamps or generate new ones
            val createdAt = settings.createdAt ?: timestamp
            val updatedAt = settings.updatedAt ?: timestamp

            // Write all settings to UserDefaults
            userDefaults.setBool(settings.cloudSyncEnabled, forKey = KEY_CLOUD_SYNC)
            userDefaults.setBool(settings.cellularSyncEnabled, forKey = KEY_CELLULAR_SYNC)
            userDefaults.setObject(settings.scanQuality, forKey = KEY_SCAN_QUALITY)
            userDefaults.setObject(createdAt, forKey = KEY_CREATED_AT)
            userDefaults.setObject(updatedAt, forKey = KEY_UPDATED_AT)
            userDefaults.setObject("synced", forKey = KEY_SYNC_STATUS) // Bulk update is usually from sync
            userDefaults.synchronize()

            println("✅ iOS AppSettings: Bulk updated settings for user $userId")
            println("   Cloud: ${settings.cloudSyncEnabled}, Cellular: ${settings.cellularSyncEnabled}, Quality: ${settings.scanQuality}")

            Result.success(settings)
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error bulk updating settings: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun clearSettings(userId: String): Result<Unit> {
        return try {
            // Remove all settings keys from UserDefaults
            userDefaults.removeObjectForKey(KEY_CLOUD_SYNC)
            userDefaults.removeObjectForKey(KEY_CELLULAR_SYNC)
            userDefaults.removeObjectForKey(KEY_SCAN_QUALITY)
            userDefaults.removeObjectForKey(KEY_CREATED_AT)
            userDefaults.removeObjectForKey(KEY_UPDATED_AT)
            userDefaults.removeObjectForKey(KEY_SYNC_STATUS)
            userDefaults.synchronize()

            println("🧹 iOS AppSettings: Cleared all settings for user $userId")

            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ iOS AppSettings: Error clearing settings: ${e.message}")
            Result.failure(e)
        }
    }
}
