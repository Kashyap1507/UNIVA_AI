package com.kashyap.wizlyai.data.local.entity

import androidx.room.*
import com.kashyap.wizlyai.data.models.DocumentPage
import com.kashyap.wizlyai.data.models.ProcessingStatus

/**
 * Room entity for document pages
 */
@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["document_id"]),
        Index(value = ["page_number"]),
        Index(value = ["sync_status"]),
    ],
)
data class DocumentPageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "image_path")
    val imagePath: String,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "width")
    val width: Int? = null,
    @ColumnInfo(name = "height")
    val height: Int? = null,
    @ColumnInfo(name = "file_size")
    val fileSize: Long? = null,
    @ColumnInfo(name = "remote_image_url")
    val remoteImageUrl: String? = null, // GCS URL from backend
    @ColumnInfo(name = "remote_thumbnail_url")
    val remoteThumbnailUrl: String? = null, // GCS thumbnail URL from backend
    @ColumnInfo(name = "storage_path")
    val storagePath: String? = null, // GCS internal path
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending",
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
    fun toModel(): DocumentPage {
        return DocumentPage(
            id = id,
            documentId = documentId,
            pageNumber = pageNumber,
            imageUrl = imagePath, // Use local path as URL for offline
            thumbnailUrl = thumbnailPath,
            width = width,
            height = height,
            fileSize = fileSize,
            status = ProcessingStatus.PENDING,
            uploadedAt = uploadedAt,
            processedAt = null,
        )
    }

    companion object {
        /**
         * Convert domain model to entity
         */
        fun fromModel(
            page: DocumentPage,
            imagePath: String,
            thumbnailPath: String? = null,
            syncStatus: String = "pending",
            lastSyncedAt: String? = null,
        ): DocumentPageEntity {
            return DocumentPageEntity(
                id = page.id,
                documentId = page.documentId,
                pageNumber = page.pageNumber,
                imagePath = imagePath,
                thumbnailPath = thumbnailPath,
                width = page.width,
                height = page.height,
                fileSize = page.fileSize,
                uploadedAt = page.uploadedAt,
                syncStatus = syncStatus,
                lastSyncedAt = lastSyncedAt,
            )
        }
    }
}
