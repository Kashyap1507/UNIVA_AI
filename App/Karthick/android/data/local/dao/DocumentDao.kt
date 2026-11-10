package com.kashyap.wizlyai.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.kashyap.wizlyai.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Document operations with pagination support
 */
@Dao
interface DocumentDao {
    // ========== INSERT ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>)

    // ========== UPDATE ==========

    @Update
    suspend fun update(document: DocumentEntity)

    @Query(
        """
        UPDATE documents
        SET title = :title,
            updated_at = :updatedAt,
            sync_status = 'pending'
        WHERE id = :documentId
    """,
    )
    suspend fun updateTitle(
        documentId: String,
        title: String,
        updatedAt: String,
    )

    @Query(
        """
        UPDATE documents
        SET sync_status = :status,
            last_synced_at = :lastSyncedAt
        WHERE id = :documentId
    """,
    )
    suspend fun updateSyncStatus(
        documentId: String,
        status: String,
        lastSyncedAt: String?,
    )

    @Query(
        """
        UPDATE documents
        SET page_count = :pageCount,
            updated_at = :updatedAt
        WHERE id = :documentId
    """,
    )
    suspend fun updatePageCount(
        documentId: String,
        pageCount: Int,
        updatedAt: String,
    )

    @Query(
        """
        UPDATE documents
        SET thumbnail_path = :thumbnailPath
        WHERE id = :documentId
    """,
    )
    suspend fun updateThumbnailPath(
        documentId: String,
        thumbnailPath: String,
    )

    // ========== DELETE ==========

    @Query(
        """
        UPDATE documents
        SET is_deleted = 1,
            deleted_at = :deletedAt,
            sync_status = 'pending'
        WHERE id = :documentId
    """,
    )
    suspend fun markAsDeleted(
        documentId: String,
        deletedAt: String,
    )

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun hardDelete(documentId: String)

    @Query("DELETE FROM documents WHERE is_deleted = 1 AND sync_status = 'synced'")
    suspend fun cleanupSyncedDeleted()

    // ========== QUERY - Single Document ==========

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun getByIdFlow(documentId: String): Flow<DocumentEntity?>

    // ========== QUERY - List with Pagination ==========

    @Query(
        """
        SELECT * FROM documents
        WHERE user_id = :userId
        AND is_deleted = 0
        ORDER BY created_at DESC
    """,
    )
    fun getDocumentsPagingSource(userId: String): PagingSource<Int, DocumentEntity>

    @Query(
        """
        SELECT * FROM documents
        WHERE user_id = :userId
        AND is_deleted = 0
        AND type = :type
        ORDER BY created_at DESC
    """,
    )
    fun getDocumentsByTypePagingSource(
        userId: String,
        type: String,
    ): PagingSource<Int, DocumentEntity>

    // ========== QUERY - Search ==========

    @Query(
        """
        SELECT * FROM documents
        WHERE user_id = :userId
        AND is_deleted = 0
        AND title LIKE '%' || :query || '%'
        ORDER BY created_at DESC
        LIMIT :limit
    """,
    )
    suspend fun searchDocuments(
        userId: String,
        query: String,
        limit: Int = 50,
    ): List<DocumentEntity>

    // ========== QUERY - Sync Queue ==========

    @Query(
        """
        SELECT * FROM documents
        WHERE (sync_status = 'pending' OR sync_status = 'failed')
        AND is_deleted = 0
        ORDER BY created_at ASC
    """,
    )
    suspend fun getPendingSyncDocuments(): List<DocumentEntity>

    @Query(
        """
        SELECT * FROM documents
        WHERE is_deleted = 1
        AND (sync_status = 'pending' OR sync_status = 'failed')
        ORDER BY deleted_at ASC
    """,
    )
    suspend fun getPendingDeletedDocuments(): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents WHERE sync_status = 'pending' OR sync_status = 'failed'")
    fun getPendingSyncCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE user_id = :userId AND is_deleted = 0")
    suspend fun getDocumentCount(userId: String): Int
}
