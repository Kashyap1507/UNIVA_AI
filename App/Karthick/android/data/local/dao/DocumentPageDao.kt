package com.kashyap.wizlyai.data.local.dao

import androidx.room.*
import com.kashyap.wizlyai.data.local.entity.DocumentPageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for DocumentPage operations
 */
@Dao
interface DocumentPageDao {
    // ========== INSERT ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: DocumentPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<DocumentPageEntity>)

    // ========== UPDATE ==========

    @Update
    suspend fun update(page: DocumentPageEntity)

    @Query(
        """
        UPDATE document_pages
        SET sync_status = :status,
            last_synced_at = :lastSyncedAt
        WHERE id = :pageId
    """,
    )
    suspend fun updateSyncStatus(
        pageId: String,
        status: String,
        lastSyncedAt: String?,
    )

    // ========== DELETE ==========

    @Query(
        """
        UPDATE document_pages
        SET is_deleted = 1,
            deleted_at = :deletedAt,
            sync_status = 'pending'
        WHERE id = :pageId
    """,
    )
    suspend fun markAsDeleted(
        pageId: String,
        deletedAt: String,
    )

    @Query("DELETE FROM document_pages WHERE id = :pageId")
    suspend fun hardDelete(pageId: String)

    @Query("DELETE FROM document_pages WHERE document_id = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    // ========== QUERY ==========

    @Query("SELECT * FROM document_pages WHERE id = :pageId")
    suspend fun getById(pageId: String): DocumentPageEntity?

    @Query(
        """
        SELECT * FROM document_pages
        WHERE document_id = :documentId
        AND is_deleted = 0
        ORDER BY page_number ASC
    """,
    )
    suspend fun getPagesByDocumentId(documentId: String): List<DocumentPageEntity>

    @Query(
        """
        SELECT * FROM document_pages
        WHERE document_id = :documentId
        AND is_deleted = 0
        ORDER BY page_number ASC
    """,
    )
    fun getPagesByDocumentIdFlow(documentId: String): Flow<List<DocumentPageEntity>>

    @Query(
        """
        SELECT * FROM document_pages
        WHERE document_id = :documentId
        AND page_number = :pageNumber
        AND is_deleted = 0
    """,
    )
    suspend fun getPageByNumber(
        documentId: String,
        pageNumber: Int,
    ): DocumentPageEntity?

    // ========== QUERY - Sync Queue ==========

    @Query(
        """
        SELECT * FROM document_pages
        WHERE (sync_status = 'pending' OR sync_status = 'failed')
        AND is_deleted = 0
        ORDER BY uploaded_at ASC
    """,
    )
    suspend fun getPendingSyncPages(): List<DocumentPageEntity>

    @Query(
        """
        SELECT * FROM document_pages
        WHERE document_id = :documentId
        AND (sync_status = 'pending' OR sync_status = 'failed')
        AND is_deleted = 0
    """,
    )
    suspend fun getPendingSyncPagesByDocument(documentId: String): List<DocumentPageEntity>

    // ========== QUERY - Statistics ==========

    @Query("SELECT COUNT(*) FROM document_pages WHERE document_id = :documentId AND is_deleted = 0")
    suspend fun getPageCount(documentId: String): Int

    @Query(
        """
        SELECT MAX(page_number) FROM document_pages
        WHERE document_id = :documentId
        AND is_deleted = 0
    """,
    )
    suspend fun getMaxPageNumber(documentId: String): Int?
}
