package com.kashyap.wizlyai.data.local

import android.content.Context
import com.kashyap.wizlyai.data.models.ScanQuality
import com.kashyap.wizlyai.util.ImageCompressor
import com.kashyap.wizlyai.util.ImageQualityProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages internal file storage for document images
 *
 * Storage structure:
 * /data/data/com.kashyap.wizlyai.android/files/documents/
 *   ├── {userId}/
 *   │   ├── {documentId}/
 *   │   │   ├── page_1_original.jpg
 *   │   │   ├── page_1_thumb.jpg
 *   │   │   ├── page_2_original.jpg
 *   │   │   └── page_2_thumb.jpg
 */
class FileStorageManager(
    private val context: Context,
    private val imageCompressor: ImageCompressor,
) {
    private val qualityProcessor = ImageQualityProcessor()

    private val documentsDir: File
        get() =
            File(context.filesDir, "documents").apply {
                if (!exists()) mkdirs()
            }

    /**
     * Save image from temp location to internal storage with compression
     *
     * Applies user's scan quality preset before saving:
     * - Resizes to preset dimensions (SMALL/STANDARD/LARGE/HD)
     * - Compresses with quality-specific JPEG compression
     * - Generates thumbnail for UI preview
     *
     * @param tempFilePath Path to temporary image file (from camera/gallery)
     * @param userId Owner (user) ID for organizing storage
     * @param documentId Document ID
     * @param pageNumber Page number (1-indexed)
     * @param scanQuality User's scan quality setting (defaults to LARGE)
     * @return Result with final internal storage path
     */
    suspend fun saveImage(
        tempFilePath: String,
        userId: String,
        documentId: String,
        pageNumber: Int,
        scanQuality: ScanQuality = ScanQuality.LARGE,
    ): Result<SavedImagePaths> {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File(tempFilePath)
                if (!tempFile.exists()) {
                    return@withContext Result.failure(Exception("Temp file does not exist: $tempFilePath"))
                }

                // Create directory structure
                val documentDir = getDocumentDirectory(userId, documentId)

                // Define output paths
                val originalFile = File(documentDir, "page_${pageNumber}_original.jpg")
                val thumbnailFile = File(documentDir, "page_${pageNumber}_thumb.jpg")

                // Apply quality preset (resize + compress based on user setting)
                val qualityResult =
                    qualityProcessor.processImage(
                        inputPath = tempFilePath,
                        quality = scanQuality,
                        outputPath = originalFile.absolutePath,
                    )

                if (qualityResult.isFailure) {
                    return@withContext Result.failure(
                        qualityResult.exceptionOrNull() ?: Exception("Quality processing failed"),
                    )
                }

                // Generate thumbnail
                val thumbnailResult = imageCompressor.generateThumbnail(originalFile, thumbnailFile)
                if (thumbnailResult.isFailure) {
                    // Thumbnail generation failed, but we have the original
                    println("Warning: Thumbnail generation failed: ${thumbnailResult.exceptionOrNull()?.message}")
                }

                // Get image metadata (after quality processing)
                val dimensions = imageCompressor.getImageDimensions(originalFile)
                val fileSize = imageCompressor.getFileSize(originalFile)

                // Clean up temp file
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    println("Warning: Failed to delete temp file: ${e.message}")
                }

                Result.success(
                    SavedImagePaths(
                        originalPath = originalFile.absolutePath,
                        thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null,
                        width = dimensions?.first,
                        height = dimensions?.second,
                        fileSize = fileSize,
                    ),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Compress existing image for upload
     * Creates a separate upload-optimized version
     */
    suspend fun prepareForUpload(originalPath: String): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val originalFile = File(originalPath)
                val uploadFile = File(originalFile.parent, "${originalFile.nameWithoutExtension}_upload.jpg")

                imageCompressor.compressForUpload(originalFile, uploadFile)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Delete all files for a document
     */
    suspend fun deleteDocumentFiles(
        userId: String,
        documentId: String,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val documentDir = getDocumentDirectory(userId, documentId, createIfMissing = false)
                if (documentDir.exists()) {
                    documentDir.deleteRecursively()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Delete a specific page's files
     */
    suspend fun deletePageFiles(
        userId: String,
        documentId: String,
        pageNumber: Int,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val documentDir = getDocumentDirectory(userId, documentId, createIfMissing = false)
                val originalFile = File(documentDir, "page_${pageNumber}_original.jpg")
                val thumbnailFile = File(documentDir, "page_${pageNumber}_thumb.jpg")

                originalFile.delete()
                thumbnailFile.delete()

                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Get path to original image
     */
    fun getOriginalImagePath(
        userId: String,
        documentId: String,
        pageNumber: Int,
    ): String {
        val documentDir = getDocumentDirectory(userId, documentId, createIfMissing = false)
        return File(documentDir, "page_${pageNumber}_original.jpg").absolutePath
    }

    /**
     * Get path to thumbnail image
     */
    fun getThumbnailPath(
        userId: String,
        documentId: String,
        pageNumber: Int,
    ): String {
        val documentDir = getDocumentDirectory(userId, documentId, createIfMissing = false)
        return File(documentDir, "page_${pageNumber}_thumb.jpg").absolutePath
    }

    /**
     * Check if original image exists
     */
    fun originalImageExists(
        userId: String,
        documentId: String,
        pageNumber: Int,
    ): Boolean {
        return File(getOriginalImagePath(userId, documentId, pageNumber)).exists()
    }

    /**
     * Get total storage used by documents (in bytes)
     */
    suspend fun getTotalStorageUsed(): Long {
        return withContext(Dispatchers.IO) {
            try {
                calculateDirectorySize(documentsDir)
            } catch (e: Exception) {
                0L
            }
        }
    }

    /**
     * Clean up orphaned upload files
     */
    suspend fun cleanupUploadFiles(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                var count = 0
                documentsDir.walk().forEach { file ->
                    if (file.isFile && file.name.endsWith("_upload.jpg")) {
                        file.delete()
                        count++
                    }
                }
                Result.success(count)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ========== PRIVATE HELPERS ==========

    private fun getDocumentDirectory(
        userId: String,
        documentId: String,
        createIfMissing: Boolean = true,
    ): File {
        val childDir = File(documentsDir, userId)
        val documentDir = File(childDir, documentId)

        if (createIfMissing && !documentDir.exists()) {
            documentDir.mkdirs()
        }

        return documentDir
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        directory.walk().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
}

/**
 * Result of saving an image with paths and metadata
 */
data class SavedImagePaths(
    val originalPath: String,
    val thumbnailPath: String?,
    val width: Int?,
    val height: Int?,
    val fileSize: Long,
)
