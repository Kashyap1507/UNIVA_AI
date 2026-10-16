package com.kashyap.wizlyai.data.repository

import android.content.Context
import com.kashyap.wizlyai.data.local.WizlyDatabase
import com.kashyap.wizlyai.data.local.entity.AppSettingsEntity
import com.kashyap.wizlyai.data.local.mapper.EntityMapper
import com.kashyap.wizlyai.network.models.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Platform-specific factory for Android
 */
actual fun createAppSettingsRepository(platformContext: Any?): AppSettingsRepository {
    require(platformContext is Context) { "Android implementation requires Context" }
    return AppSettingsRepositoryImpl(platformContext)
}

/**
 * Android implementation of AppSettingsRepository using Room database
 *
 * @param context Android context for database access
 */
private class AppSettingsRepositoryImpl(private val context: Context) : AppSettingsRepository {
    private val database = WizlyDatabase.getDatabase(context)
    private val appSettingsDao = database.appSettingsDao()

    override suspend fun getSettings(userId: String): Result<AppSettings> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val entity = appSettingsDao.getByUserId(userId)

                if (entity != null) {
                    // Convert entity to model
                    val settings = EntityMapper.toModel(entity)
                    Result.success(settings)
                } else {
                    // No settings yet - return defaults
                    val defaultSettings =
                        AppSettings(
                            cloudSyncEnabled = false,
                            cellularSyncEnabled = false,
                            scanQuality = "LARGE",
                            createdAt = Clock.System.now().toString(),
                            updatedAt = Clock.System.now().toString(),
                        )
                    Result.success(defaultSettings)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun getSettingsFlow(userId: String): Flow<AppSettings?> {
        return appSettingsDao.getByUserIdFlow(userId).map { entity ->
            entity?.let { EntityMapper.toModel(it) }
        }
    }

    override suspend fun updateCloudSync(
        userId: String,
        enabled: Boolean,
    ): Result<AppSettings> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val timestamp = Clock.System.now().toString()

                // Check if settings exist
                val existingSettings = appSettingsDao.getByUserId(userId)

                if (existingSettings != null) {
                    // Update existing settings
                    appSettingsDao.updateCloudSyncEnabled(
                        userId = userId,
                        enabled = enabled,
                        updatedAt = timestamp,
                        syncStatus = "pending", // Mark for backend upload
                    )

                    // Return updated settings
                    val updatedEntity = appSettingsDao.getByUserId(userId)!!
                    Result.success(EntityMapper.toModel(updatedEntity))
                } else {
                    // Create new settings
                    val newEntity =
                        AppSettingsEntity(
                            userId = userId,
                            cloudSyncEnabled = enabled,
                            createdAt = timestamp,
                            updatedAt = timestamp,
                            syncStatus = "pending",
                        )
                    appSettingsDao.insert(newEntity)

                    Result.success(EntityMapper.toModel(newEntity))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updateCellularSync(
        userId: String,
        enabled: Boolean,
    ): Result<AppSettings> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val timestamp = Clock.System.now().toString()

                // Check if settings exist
                val existingSettings = appSettingsDao.getByUserId(userId)

                if (existingSettings != null) {
                    // Update existing settings
                    appSettingsDao.updateCellularSyncEnabled(
                        userId = userId,
                        enabled = enabled,
                        updatedAt = timestamp,
                        syncStatus = "pending", // Mark for backend upload
                    )

                    // Return updated settings
                    val updatedEntity = appSettingsDao.getByUserId(userId)!!
                    Result.success(EntityMapper.toModel(updatedEntity))
                } else {
                    // Create new settings with cellular sync enabled
                    val newEntity =
                        AppSettingsEntity(
                            userId = userId,
                            cloudSyncEnabled = false, // Default
                            cellularSyncEnabled = enabled,
                            createdAt = timestamp,
                            updatedAt = timestamp,
                            syncStatus = "pending",
                        )
                    appSettingsDao.insert(newEntity)

                    Result.success(EntityMapper.toModel(newEntity))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updateScanQuality(
        userId: String,
        quality: String,
    ): Result<AppSettings> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val timestamp = Clock.System.now().toString()

                // Check if settings exist
                val existingSettings = appSettingsDao.getByUserId(userId)

                if (existingSettings != null) {
                    // Update existing settings
                    appSettingsDao.updateScanQuality(
                        userId = userId,
                        quality = quality,
                        updatedAt = timestamp,
                        syncStatus = "pending", // Mark for backend upload
                    )

                    // Return updated settings
                    val updatedEntity = appSettingsDao.getByUserId(userId)!!
                    Result.success(EntityMapper.toModel(updatedEntity))
                } else {
                    // Create new settings with scan quality
                    val newEntity =
                        AppSettingsEntity(
                            userId = userId,
                            cloudSyncEnabled = false, // Default
                            scanQuality = quality,
                            createdAt = timestamp,
                            updatedAt = timestamp,
                            syncStatus = "pending",
                        )
                    appSettingsDao.insert(newEntity)

                    Result.success(EntityMapper.toModel(newEntity))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updateSettings(
        userId: String,
        settings: AppSettings,
    ): Result<AppSettings> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val timestamp = Clock.System.now().toString()

                val entity =
                    AppSettingsEntity(
                        userId = userId,
                        cloudSyncEnabled = settings.cloudSyncEnabled,
                        cellularSyncEnabled = settings.cellularSyncEnabled,
                        scanQuality = settings.scanQuality,
                        createdAt = settings.createdAt ?: timestamp,
                        updatedAt = timestamp, // Always update timestamp
                        syncStatus = "pending", // Mark for backend upload
                    )

                // Insert or replace
                appSettingsDao.insert(entity)

                Result.success(EntityMapper.toModel(entity))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun clearSettings(userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val entity = appSettingsDao.getByUserId(userId)
                if (entity != null) {
                    appSettingsDao.delete(entity)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
