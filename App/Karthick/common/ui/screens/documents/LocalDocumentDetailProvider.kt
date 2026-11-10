package com.kashyap.wizlyai.ui.screens.documents

import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.repository.DocumentWithPages

/**
 * Local-first document detail provider
 *
 * Loads document details from local storage with caching
 *
 * Platform-specific implementations:
 * - Android: Room database with SimpleCache
 * - iOS: CoreData with in-memory caching
 */
expect class LocalDocumentDetailProvider {
    /**
     * Get document with pages from local storage
     *
     * @param documentId Document ID
     * @return Result with Document and List of Pages
     */
    suspend fun getDocumentWithPages(documentId: String): Result<DocumentWithPages>

    /**
     * Get document metadata only (without pages)
     *
     * @param documentId Document ID
     * @return Result with Document
     */
    suspend fun getDocument(documentId: String): Result<Document>

    /**
     * Invalidate cache for specific document
     *
     * @param documentId Document ID to invalidate
     */
    fun invalidateDocument(documentId: String)

    /**
     * Invalidate all document detail caches
     */
    fun invalidateAll()

    /**
     * Prefetch document for faster access later
     *
     * @param documentId Document ID to prefetch
     */
    suspend fun prefetch(documentId: String)

    /**
     * Prefetch multiple documents
     *
     * @param documentIds List of document IDs to prefetch
     */
    suspend fun prefetchMultiple(documentIds: List<String>)
}
