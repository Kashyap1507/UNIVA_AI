package com.kashyap.wizlyai.data.local.mapper

import com.kashyap.wizlyai.data.local.entity.*
import com.kashyap.wizlyai.network.models.*
import kotlinx.datetime.Clock

/**
 * Centralized mapper for converting API models to Room entities
 */
object EntityMapper {
    /**
     * Map AuthUser to UserEntity
     * Used when syncing user data from authentication response
     *
     * @param user AuthUser from authentication response
     * @param timestamp Override timestamp (defaults to current time)
     * @return UserEntity ready for Room insertion
     */
    fun toEntity(
        user: AuthUser,
        timestamp: String = Clock.System.now().toString(),
    ): UserEntity {
        return UserEntity(
            id = user.id,
            email = user.email,
            name = user.name ?: user.email.substringBefore("@"),
            authProvider = user.authProvider,
            createdAt = timestamp,
            updatedAt = timestamp,
            lastLogin = timestamp,
            syncStatus = "synced",
            subscriptionTier = "free", // Default for AuthUser
            subscriptionStatus = "active",
            accountType = "full",
            referralCode = null,
            referralLimit = 5,
            referralsRemaining = 5,
        )
    }

    /**
     * Map the user profile to UserEntity
     * Used when syncing the profile from bootstrap data
     * Includes subscription and referral fields
     *
     * @param profile Profile from bootstrap response
     * @param timestamp Override timestamp (defaults to current time)
     * @return UserEntity ready for Room insertion
     */
    fun toEntity(
        profile: UserProfile,
        timestamp: String = Clock.System.now().toString(),
    ): UserEntity {
        return UserEntity(
            id = profile.id,
            email = profile.email,
            name = profile.name ?: profile.email.substringBefore("@"),
            authProvider = profile.authProvider,
            createdAt = profile.createdAt ?: timestamp,
            updatedAt = profile.updatedAt ?: timestamp,
            lastLogin = profile.lastLogin ?: timestamp,
            syncStatus = "synced",
            subscriptionTier = "free", // Will be updated from SubscriptionData
            subscriptionStatus = "active",
            accountType = profile.accountType,
            referralCode = profile.referralCode,
            referralLimit = profile.referralLimit,
            referralsRemaining = profile.referralsRemaining,
        )
    }

    /**
     * Map SubscriptionData to List of UsageStatsEntity
     * Converts API subscription usage data to Room entities
     *
     * @param subscriptionData SubscriptionData from bootstrap response
     * @param userId User ID for foreign key relationship
     * @param timestamp Current timestamp
     * @return List of UsageStatsEntity ready for Room insertion
     */
    fun toUsageStatsEntities(
        subscriptionData: SubscriptionData,
        userId: String,
        timestamp: String = Clock.System.now().toString(),
    ): List<UsageStatsEntity> {
        return subscriptionData.usageSummary.features.map { (featureName, featureUsage) ->
            UsageStatsEntity(
                userId = userId,
                featureName = featureName,
                usedCount = featureUsage.used,
                limitCount = featureUsage.limit,
                resetTime = subscriptionData.resetTime,
                lastUpdated = timestamp,
            )
        }
    }

    /**
     * Map AppSettings (from network) to AppSettingsEntity
     * Used during bootstrap sync after authentication
     *
     * Handles optional timestamps with sensible defaults.
     * If backend doesn't provide settings, creates default settings with cloudSync disabled.
     *
     * @param settings AppSettings from network response (null = use defaults)
     * @param userId User ID for foreign key relationship
     * @param timestamp Override timestamp (defaults to current time)
     * @return AppSettingsEntity ready for Room insertion
     */
    fun toEntity(
        settings: AppSettings?,
        userId: String,
        timestamp: String = Clock.System.now().toString(),
    ): AppSettingsEntity {
        return if (settings != null) {
            AppSettingsEntity(
                userId = userId,
                cloudSyncEnabled = settings.cloudSyncEnabled,
                cellularSyncEnabled = settings.cellularSyncEnabled,
                scanQuality = settings.scanQuality,
                createdAt = settings.createdAt ?: timestamp,
                updatedAt = settings.updatedAt ?: timestamp,
                syncStatus = "synced",
            )
        } else {
            // Default settings if backend doesn't provide them
            AppSettingsEntity.createDefault(userId, timestamp)
        }
    }

    /**
     * Map AppSettingsEntity to AppSettings network model
     * Used when uploading settings to backend
     *
     * @param entity AppSettingsEntity from Room
     * @return AppSettings ready for network upload
     */
    fun toModel(entity: AppSettingsEntity): AppSettings {
        return AppSettings(
            cloudSyncEnabled = entity.cloudSyncEnabled,
            cellularSyncEnabled = entity.cellularSyncEnabled,
            scanQuality = entity.scanQuality,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}
