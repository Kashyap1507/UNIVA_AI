package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentType

/**
 * Platform-agnostic interface for local document storage
 * Android implements with Room, iOS will implement with Core Data or other local storage
 */
interface LocalDocumentStorage {
    /**
     * Save document locally with pages
     * Returns local document ID
     */
    suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>, // Paths to captured images in temp storage
    ): Result<String>

    /**
     * Save document with specific ID (for API documents)
     * Preserves the document ID from the server
     * Returns the same document ID on success
     */
    suspend fun saveDocumentWithId(
        documentId: String,
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String>

    /**
     * Keep a copy of a scan that is already on the server (Check my work uploads it first),
     * so it shows in the scans library. Keeps the server's document id and queues nothing
     * for upload. Platforms without this support report a failure.
     */
    suspend fun saveUploadedScan(
        documentId: String,
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> = Result.failure(UnsupportedOperationException("Saving uploaded scans locally is not supported here"))

    /**
     * Delete a scan and its pages from this device. The server copy is kept
     * (Check my work history still refers to it).
     */
    suspend fun deleteDocumentLocally(
        documentId: String,
        userId: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Deleting scans is not supported here"))

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
     * Get document with pages from local storage
     */
    suspend fun getLocalDocument(documentId: String): Result<Document>

    /**
     * Mark document as pending sync
     */
    suspend fun markForSync(documentId: String): Result<Unit>

    /**
     * Get storage usage in bytes
     */
    suspend fun getStorageUsed(): Long
}

/**
 * No-op implementation for platforms without local storage
 */
class NoOpLocalDocumentStorage : LocalDocumentStorage {
    override suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> = Result.failure(NotImplementedError("Local storage not implemented for this platform"))

    override suspend fun saveDocumentWithId(
        documentId: String,
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> = Result.failure(NotImplementedError("Local storage not implemented for this platform"))

    override suspend fun addPageLocally(
        documentId: String,
        imagePath: String,
    ): Result<Unit> = Result.failure(NotImplementedError("Local storage not implemented for this platform"))

    override suspend fun deletePageLocally(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
    ): Result<Unit> = Result.failure(NotImplementedError("Local storage not implemented for this platform"))

    override suspend fun getLocalDocument(documentId: String): Result<Document> =
        Result.failure(NotImplementedError("Local storage not implemented for this platform"))

    override suspend fun markForSync(documentId: String): Result<Unit> =
        Result.failure(NotImplementedError("Local storage not implemented for this platform"))

    override suspend fun getStorageUsed(): Long = 0L
}
