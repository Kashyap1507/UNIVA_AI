package com.kashyap.wizlyai.upload

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Direct upload service interface for uploading files directly to Firebase Storage
 *
 * This bypasses the backend for the actual file transfer, reducing latency by ~50%.
 * Platform-specific implementations handle the HTTP PUT to signed URLs.
 */
expect class DirectUploadService() {
    /**
     * Upload file data directly to Firebase Storage using a signed URL
     *
     * @param signedUrl Pre-signed URL from backend for PUT upload
     * @param fileData Compressed image data to upload
     * @param contentType MIME type of the file
     * @param headers Additional headers (e.g., Content-Length-Range)
     * @param progressCallback Optional callback for upload progress (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun uploadToSignedUrl(
        signedUrl: String,
        fileData: ByteArray,
        contentType: String,
        headers: Map<String, String>,
        progressCallback: ((Float) -> Unit)?
    ): Result<Unit>

    /**
     * Upload file from path directly to Firebase Storage
     *
     * @param signedUrl Pre-signed URL for upload
     * @param filePath Path to the file to upload
     * @param contentType MIME type of the file
     * @param headers Additional headers
     * @param progressCallback Optional callback for upload progress
     * @return Result indicating success or failure
     */
    suspend fun uploadFileToSignedUrl(
        signedUrl: String,
        filePath: String,
        contentType: String,
        headers: Map<String, String>,
        progressCallback: ((Float) -> Unit)?
    ): Result<Long>
}

/**
 * Upload state for tracking upload progress
 */
sealed class UploadState {
    object Idle : UploadState()
    object Compressing : UploadState()
    object RequestingUrl : UploadState()
    data class Uploading(val progress: Float) : UploadState()
    object Completing : UploadState()
    data class Success(val photoId: String?, val publicUrl: String?) : UploadState()
    data class Error(val message: String, val cause: Throwable? = null) : UploadState()
}

/**
 * Upload result with timing information
 */
data class OptimizedUploadResult(
    val photoId: String?,
    val documentId: String?,
    val publicUrl: String?,
    val totalTimeMs: Long,
    val compressionTimeMs: Long,
    val uploadTimeMs: Long,
    val fileSizeBytes: Long
) {
    val uploadSpeedMbps: Double
        get() = if (uploadTimeMs > 0) {
            (fileSizeBytes * 8.0 / 1_000_000) / (uploadTimeMs / 1000.0)
        } else 0.0
}
