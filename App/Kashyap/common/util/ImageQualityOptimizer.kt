package com.kashyap.wizlyai.util

/**
 * Image Quality Optimization Settings
 */
data class ImageQualitySettings(
    val jpegQuality: Int = 90, // 0-100, higher = better quality
    val maxDimension: Int = 4096, // Maximum width or height for processing
    val targetFileSize: Long = 3_000_000L, // Target 3 MB per page
    val enableSharpening: Boolean = true,
)

/**
 * Image Quality Optimizer (expect/actual)
 * Platform-specific image quality optimization
 */
expect class ImageQualityOptimizer() {
    /**
     * Downsample large image to max dimension while maintaining aspect ratio
     * @param inputPath Path to input image
     * @param outputPath Path for output image (can be same as input)
     * @param maxDimension Maximum dimension (width or height)
     * @return Path to downsampled image, or null if operation failed
     */
    fun downsampleImage(
        inputPath: String,
        outputPath: String,
        maxDimension: Int = ImageValidationConstants.MAX_PROCESSING_DIMENSION,
    ): String?

    /**
     * Optimize JPEG quality based on content analysis
     * Text-heavy images get higher quality, photos get lower quality
     * @param imagePath Path to image
     * @return Recommended JPEG quality (0-100)
     */
    fun calculateOptimalQuality(imagePath: String): Int

    /**
     * Apply sharpening filter to image (useful after perspective transform)
     * @param inputPath Path to input image
     * @param outputPath Path for output image
     * @param strength Sharpening strength (0.0 to 1.0)
     * @return Path to sharpened image, or null if operation failed
     */
    fun sharpenImage(
        inputPath: String,
        outputPath: String,
        strength: Float = 0.3f,
    ): String?

    /**
     * Compress image to target file size
     * Automatically adjusts JPEG quality to meet target
     * @param inputPath Path to input image
     * @param outputPath Path for output image
     * @param targetSizeBytes Target file size in bytes
     * @return Path to compressed image, or null if operation failed
     */
    fun compressToSize(
        inputPath: String,
        outputPath: String,
        targetSizeBytes: Long,
    ): String?

    /**
     * Optimize image for upload (downsample + quality optimization + sharpening)
     * @param inputPath Path to input image
     * @param outputPath Path for output image
     * @param settings Quality settings
     * @return Path to optimized image, or null if operation failed
     */
    fun optimizeForUpload(
        inputPath: String,
        outputPath: String,
        settings: ImageQualitySettings = ImageQualitySettings(),
    ): String?
}

/**
 * Quality constants
 */
object ImageQualityConstants {
    const val HIGH_QUALITY_JPEG = 95
    const val TEXT_QUALITY_JPEG = 90
    const val BALANCED_QUALITY_JPEG = 85
    const val PHOTO_QUALITY_JPEG = 80
    const val LOW_QUALITY_JPEG = 70

    const val EDGE_DETECTION_THRESHOLD_HIGH = 0.15f // > 15% edges = text-heavy
    const val EDGE_DETECTION_THRESHOLD_LOW = 0.05f // < 5% edges = photo

    const val DEFAULT_DPI = 150
    const val HIGH_DPI = 300
}
