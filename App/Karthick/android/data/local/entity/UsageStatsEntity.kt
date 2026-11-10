package com.kashyap.wizlyai.data.local.entity

import androidx.room.*

/**
 * Room entity for usage statistics tracking
 * Stores daily usage counts for subscription features
 */
@Entity(
    tableName = "usage_stats",
    indices = [
        Index(value = ["user_id", "feature_name"], unique = true),
    ],
)
data class UsageStatsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "feature_name")
    val featureName: String, // "work_checks", "practice_sets", "flashcard_sets"

    @ColumnInfo(name = "used_count")
    val usedCount: Int = 0,

    @ColumnInfo(name = "limit_count")
    val limitCount: Int = 0, // -1 for unlimited

    @ColumnInfo(name = "reset_time")
    val resetTime: String? = null, // ISO timestamp for next reset

    @ColumnInfo(name = "last_updated")
    val lastUpdated: String, // ISO timestamp
)
