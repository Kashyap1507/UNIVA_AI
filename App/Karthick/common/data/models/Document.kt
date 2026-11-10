package com.kashyap.wizlyai.data.models

import kotlinx.serialization.Serializable

/**
 * Document model - a multi-page scan of the student's work (used by "Check my work")
 */
@Serializable
data class Document(
    val id: String,
    // Owner (the student's user id)
    val userId: String = "",
    // Basic info
    val title: String,
    val type: DocumentType = DocumentType.HOMEWORK,
    // Multi-page support
    val pageCount: Int = 0, // Number of scanned pages
    val pages: List<DocumentPage>? = null, // Included when fetching full document
    // Thumbnail (from first page)
    val thumbnailUrl: String? = null,
    // Processing status
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val processingProgress: Int = 0, // 0-100
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Document Page - Represents a single scanned image within a document
 */
@Serializable
data class DocumentPage(
    val id: String,
    val documentId: String,
    val pageNumber: Int, // 1-indexed (1, 2, 3, etc.)
    // Image data
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fileSize: Long? = null, // bytes
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val uploadedAt: String,
    val processedAt: String? = null,
)

/**
 * Document Type. Matches the backend Document.TYPE_* constants; scans of the
 * student's work are stored as HOMEWORK, WORKSHEET only appears on old records.
 */
@Serializable
enum class DocumentType {
    HOMEWORK,
    WORKSHEET,
}

/**
 * Processing Status - Current state of document processing
 */
@Serializable
enum class ProcessingStatus {
    PENDING, // Uploaded, waiting to be processed
    PROCESSING, // AI is analyzing
    COMPLETED, // All pages processed successfully
    PARTIAL, // Some pages processed, some pending/failed
    FAILED, // Processing error
}
