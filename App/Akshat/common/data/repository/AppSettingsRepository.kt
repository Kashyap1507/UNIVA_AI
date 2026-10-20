package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.models.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific factory function for creating AppSettingsRepository
 * Android: Requires Context
 * iOS: No parameters needed
 */
expect fun createAppSettingsRepository(platformContext: Any? = null): AppSettingsRepository

/**
 * Repository interface for app settings management
 *
 * Provides cross-platform access to app settings with platform-specific implementations.
 * Settings are stored in:
 * - Android: Room database (AppSettingsEntity)
 * - iOS: UserDefaults or CoreData (TBD)
 *
 * Features:
 * - Get settings for a user
 * - Update settings (triggers backend sync)
 * - Reactive Flow for UI observation
 * - Platform-agnostic interface
 *
 * Usage:
 * ```
 * val repository = createAppSettingsRepository(context)
 * val settings = repository.getSettings(userId).getOrNull()
 * repository.updateCloudSync(userId, true)
 * ```
 */
interface AppSettingsRepository {
    suspend fun getSettings(userId: String): Result<AppSettings>

    fun getSettingsFlow(userId: String): Flow<AppSettings?>

    suspend fun updateCloudSync(
        userId: String,
        enabled: Boolean,
    ): Result<AppSettings>

    suspend fun updateCellularSync(
        userId: String,
        enabled: Boolean,
    ): Result<AppSettings>

    suspend fun updateScanQuality(
        userId: String,
        quality: String,
    ): Result<AppSettings>

    suspend fun updateSettings(
        userId: String,
        settings: AppSettings,
    ): Result<AppSettings>

    suspend fun clearSettings(userId: String): Result<Unit>
}
