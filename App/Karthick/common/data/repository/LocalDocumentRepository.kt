package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentPage
import com.kashyap.wizlyai.data.models.DocumentType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for offline-first document operations
 * Handles local storage and sync queue management
 *
 * Platform-specific implementations:
 * - Android: Uses Room database with full Paging support
 * - iOS: Uses CoreData with FileStorageManager
 */
expect class LocalDocumentRepository {
    /**
     * Save document locally with pages
     * Automatically marks as pending sync
     *
     * @return Result with documentId on success
     */
    suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String>

    /**
     * Get document by ID (offline)
     */
    suspend fun getLocalDocument(documentId: String): Result<Document>

    /**
     * Add page to existing document
     */
    suspend fun addPageLocally(
        documentId: String,
        imagePath: String,
    ): Result<Unit>

    /**
     * Delete page from document
     */
    suspend fun deletePageLocally(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
    ): Result<Unit>

    /**
     * Hard delete document and all files
     */
    suspend fun hardDeleteDocument(
        documentId: String,
        userId: String,
        deleteFromServer: Boolean = true,
    ): Result<Unit>

    /**
     * Mark document for sync
     */
    suspend fun markForSync(documentId: String): Result<Unit>

    /**
     * Get pending sync count (observable)
     */
    fun getPendingSyncCountFlow(): Flow<Int>

    /**
     * Get storage usage in bytes
     */
    suspend fun getTotalStorageUsed(): Long
}

/**
 * Document with its pages
 */
data class DocumentWithPages(
    val document: Document,
    val pages: List<DocumentPage>,
)
