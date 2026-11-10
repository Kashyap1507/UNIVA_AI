package com.kashyap.wizlyai.data.local.entity

import androidx.room.*
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.data.models.ProcessingStatus

/**
 * Room entity for document scans with offline-first support (schema v6: owned by user_id)
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["created_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "type")
    val type: String, // DocumentType enum name
    @ColumnInfo(name = "page_count")
    val pageCount: Int = 0,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "status")
    val status: String = "PENDING", // ProcessingStatus enum name
    @ColumnInfo(name = "processing_progress")
    val processingProgress: Int = 0,
    @ColumnInfo(name = "remote_thumbnail_url")
    val remoteThumbnailUrl: String? = null, // GCS URL from backend
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending", // pending/syncing/synced/failed
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: String? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String? = null,
) {
    /**
     * Convert entity to domain model
     */
    fun toModel(): Document {
        // Prefer local thumbnail path with file:// prefix for offline-first loading
        // Fallback to remote URL if local not available
        val thumbnailUri =
            when {
                !thumbnailPath.isNullOrEmpty() -> "file://$thumbnailPath"
                !remoteThumbnailUrl.isNullOrEmpty() -> remoteThumbnailUrl
                else -> null
            }

        return Document(
            id = id,
            userId = userId,
            title = title,
            type = runCatching { DocumentType.valueOf(type) }.getOrDefault(DocumentType.HOMEWORK),
            pageCount = pageCount,
            thumbnailUrl = thumbnailUri,
            status = runCatching { ProcessingStatus.valueOf(status) }.getOrDefault(ProcessingStatus.PENDING),
            processingProgress = processingProgress,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        /**
         * Convert domain model to entity
         */
        fun fromModel(
            document: Document,
            syncStatus: String = "pending",
            lastSyncedAt: String? = null,
            thumbnailPath: String? = null,
        ): DocumentEntity {
            return DocumentEntity(
                id = document.id,
                userId = document.userId,
                title = document.title,
                type = document.type.name,
                pageCount = document.pageCount,
                thumbnailPath = thumbnailPath ?: document.thumbnailUrl,
                status = document.status.name,
                processingProgress = document.processingProgress,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt,
                syncStatus = syncStatus,
                lastSyncedAt = lastSyncedAt,
            )
        }
    }
}
