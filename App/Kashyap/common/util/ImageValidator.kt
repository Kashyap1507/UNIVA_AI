package com.kashyap.wizlyai.util

/**
 * Image Validation Result
 * Sealed class representing the result of image validation
 */
sealed class ImageValidationResult {
    data class Valid(val width: Int, val height: Int, val fileSize: Long) : ImageValidationResult()

    object NotFound : ImageValidationResult()

    object TooLarge : ImageValidationResult()

    object TooSmall : ImageValidationResult()

    object UnsupportedFormat : ImageValidationResult()

    object Corrupted : ImageValidationResult()

    data class Error(val message: String) : ImageValidationResult()
}

/**
 * Image Validator (expect/actual)
 * Platform-specific image validation
 */
expect class ImageValidator() {
    /**
     * Validate image file
     * @param filePath Absolute path to image file
     * @return ImageValidationResult indicating validation outcome
     */
    fun validateImage(filePath: String): ImageValidationResult

    /**
     * Check if file format is supported
     * @param filePath Absolute path to image file
     * @return true if format is supported (JPEG, PNG, HEIC)
     */
    fun isSupportedFormat(filePath: String): Boolean

    /**
     * Get file size in bytes
     * @param filePath Absolute path to image file
     * @return File size in bytes, or -1 if file doesn't exist
     */
    fun getFileSize(filePath: String): Long
}

/**
 * Image Validation Constants
 */
object ImageValidationConstants {
    const val MAX_FILE_SIZE_BYTES = 20_000_000L // 20 MB
    const val MIN_FILE_SIZE_BYTES = 1024L // 1 KB
    const val WARNING_FILE_SIZE_BYTES = 10_000_000L // 10 MB (show warning)
    const val MAX_DIMENSION = 8000 // Maximum width or height
    const val MIN_DIMENSION = 100 // Minimum width or height
    const val MAX_PROCESSING_DIMENSION = 4096 // Maximum for processing (downsample larger images)

    val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif")
    val SUPPORTED_MIME_TYPES =
        setOf(
            "image/jpeg",
            "image/png",
            "image/heic",
            "image/heif",
        )
}

/**
 * Get user-friendly error message for validation result
 */
fun ImageValidationResult.getUserMessage(): String {
    return when (this) {
        is ImageValidationResult.Valid -> "Image is valid"
        is ImageValidationResult.NotFound -> "Image file not found"
        is ImageValidationResult.TooLarge -> "Image file is too large (max 20 MB)"
        is ImageValidationResult.TooSmall -> "Image file is too small"
        is ImageValidationResult.UnsupportedFormat -> "Unsupported image format. Please use JPEG, PNG, or HEIC"
        is ImageValidationResult.Corrupted -> "Image file is corrupted or cannot be read"
        is ImageValidationResult.Error -> this.message
    }
}

/**
 * Check if validation result is valid
 */
fun ImageValidationResult.isValid(): Boolean {
    return this is ImageValidationResult.Valid
}
