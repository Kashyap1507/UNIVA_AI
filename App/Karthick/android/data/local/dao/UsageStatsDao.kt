package com.kashyap.wizlyai.data.local.dao

import androidx.room.*
import com.kashyap.wizlyai.data.local.entity.UsageStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for usage statistics operations
 */
@Dao
interface UsageStatsDao {
    /**
     * Get all usage stats for a user
     */
    @Query("SELECT * FROM usage_stats WHERE user_id = :userId")
    fun getUserUsageStats(userId: String): Flow<List<UsageStatsEntity>>

    /**
     * Get specific feature usage for a user
     */
    @Query("SELECT * FROM usage_stats WHERE user_id = :userId AND feature_name = :featureName LIMIT 1")
    suspend fun getFeatureUsage(userId: String, featureName: String): UsageStatsEntity?

    /**
     * Get specific feature usage as Flow
     */
    @Query("SELECT * FROM usage_stats WHERE user_id = :userId AND feature_name = :featureName LIMIT 1")
    fun getFeatureUsageFlow(userId: String, featureName: String): Flow<UsageStatsEntity?>

    /**
     * Insert or update usage stats
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usageStats: UsageStatsEntity)

    /**
     * Insert or update multiple usage stats
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usageStats: List<UsageStatsEntity>)

    /**
     * Update usage stats
     */
    @Update
    suspend fun update(usageStats: UsageStatsEntity)

    /**
     * Delete specific usage stat
     */
    @Delete
    suspend fun delete(usageStats: UsageStatsEntity)

    /**
     * Delete all usage stats for a user
     */
    @Query("DELETE FROM usage_stats WHERE user_id = :userId")
    suspend fun deleteUserStats(userId: String)

    /**
     * Clear all usage stats (for logout)
     */
    @Query("DELETE FROM usage_stats")
    suspend fun deleteAll()

    /**
     * Increment usage count for a feature
     */
    @Query("""
        UPDATE usage_stats
        SET used_count = used_count + 1,
            last_updated = :timestamp
        WHERE user_id = :userId AND feature_name = :featureName
    """)
    suspend fun incrementUsage(userId: String, featureName: String, timestamp: String)
}
