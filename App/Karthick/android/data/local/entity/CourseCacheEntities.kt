package com.kashyap.wizlyai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached course (JSON payload of the API model). Added in DB v5. */
@Entity(tableName = "courses", indices = [Index(value = ["user_id"])])
data class CourseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)

/** Cached course material (JSON payload of the API model). Added in DB v5. */
@Entity(tableName = "course_materials", indices = [Index(value = ["course_id"])])
data class CourseMaterialEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)
