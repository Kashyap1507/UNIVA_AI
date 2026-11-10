package com.kashyap.wizlyai.ui.screens.documents

import com.kashyap.wizlyai.data.models.Document

/**
 * Platform-agnostic interface for providing documents
 * Android implements with Paging 3, iOS can implement with Core Data or similar
 */
interface DocumentProvider {
    /**
     * Get documents for display
     * Implementation varies by platform
     * Made suspend to prevent UI blocking on iOS
     */
    suspend fun getDocuments(userId: String): List<Document>

    /**
     * Get document with all pages
     */
    suspend fun getDocumentWithPages(documentId: String): Document

    /**
     * Search documents
     */
    suspend fun searchDocuments(
        userId: String,
        query: String,
    ): List<Document>

    /**
     * Delete document
     */
    suspend fun deleteDocument(documentId: String): Result<Unit>

    /**
     * Get pending sync count
     */
    suspend fun getPendingSyncCount(): Int

    /**
     * Get storage used in bytes
     */
    suspend fun getStorageUsed(): Long
}
