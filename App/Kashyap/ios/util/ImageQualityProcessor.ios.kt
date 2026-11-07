package com.kashyap.wizlyai.util

import com.kashyap.wizlyai.data.models.ScanQuality
import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS implementation of Image Quality Processor for Document Scanning
 *
 * Applies resolution and compression presets to captured document images
 * based on user's scan quality setting.
 *
 * Features:
 * - Resize to preset dimensions (SMALL/STANDARD/LARGE/HD)
 * - Compress to target file size
 * - Preserve aspect ratio
 * - Maintain image orientation (EXIF)
 * - Memory-efficient processing for large images
 *
 * Uses Swift bridge to leverage iOS-native UIImage and Core Graphics APIs
 * for optimal performance and memory management.
 *
 * Usage:
 * ```
 * val processor = ImageQualityProcessor()
 * processor.processImage(
 *     inputPath = "/path/to/captured/image.jpg",
 *     quality = ScanQuality.LARGE,
 *     outputPath = "/path/to/processed/image.jpg"
 * ).fold(
 *     onSuccess = { processedPath -> /* Use processed image */ },
 *     onFailure = { error -> /* Handle error */ }
 * )
 * ```
 */
class ImageQualityProcessor {
    companion object {
        // JPEG quality settings for different presets
        // Higher quality = larger file size, better image
        // These match Android implementation for consistency
        const val JPEG_QUALITY_SMALL = 75 // ~0.5 MB target
        const val JPEG_QUALITY_STANDARD = 80 // ~1.5 MB target
        const val JPEG_QUALITY_LARGE = 85 // ~3.0 MB target
        const val JPEG_QUALITY_HD = 90 // ~5.0 MB target
    }

    /**
     * Process captured image with quality preset
     *
     * Reads the input image, applies quality preset (resize + compress),
     * and saves to output path using Swift implementation.
     *
     * @param inputPath Path to captured image (raw camera output)
     * @param quality Scan quality preset
     * @param outputPath Path to save processed image (defaults to replacing input)
     * @return Result with processed image path or error
     */
    fun processImage(
        inputPath: String,
        quality: ScanQuality,
        outputPath: String? = null,
    ): Result<String> {
        println("📸 iOS: Processing image with quality ${quality.displayName}")
        println("   Input: $inputPath")
        println("   Target dimensions: ${quality.width}x${quality.height}")
        println("   Target size: ${quality.estimatedSizeMB} MB")

        return try {
            val callbacks = IOSCallbackBridge.getImageQualityProcessorCallbacks()
            if (callbacks == null) {
                println("   ❌ ImageQualityProcessor callbacks not set!")
                return Result.failure(
                    Exception("ImageQualityProcessor callbacks not initialized"),
                )
            }

            // Call Swift implementation
            var processedPath: String? = null
            var error: Exception? = null

            callbacks.processImage(inputPath, quality, outputPath) { result ->
                result.fold(
                    onSuccess = { path ->
                        processedPath = path
                        println("   ✅ Image processed successfully")
                        println("   Output: $path")
                    },
                    onFailure = { e ->
                        error = e as? Exception ?: Exception(e.message ?: "Unknown error")
                        println("   ❌ Processing failed: ${e.message}")
                    },
                )
            }

            // Return result
            processedPath?.let {
                Result.success(it)
            } ?: error?.let {
                Result.failure(it)
            } ?: Result.failure(Exception("Unknown error during image processing"))
        } catch (e: Exception) {
            println("   ❌ Exception during processing: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get processed file size estimate
     *
     * Useful for UI feedback before processing.
     *
     * @param quality Scan quality preset
     * @return Estimated file size in MB
     */
    fun getEstimatedFileSize(quality: ScanQuality): Double {
        return quality.estimatedSizeMB
    }

    /**
     * Check if processing is needed
     *
     * If image is already at or below target quality dimensions,
     * processing may be skipped (just copy file).
     *
     * @param imagePath Path to image
     * @param quality Target quality
     * @return True if processing recommended, false if can skip
     */
    fun shouldProcess(
        imagePath: String,
        quality: ScanQuality,
    ): Boolean {
        println("📸 iOS: Checking if processing needed for '$imagePath'")

        return try {
            val callbacks = IOSCallbackBridge.getImageQualityProcessorCallbacks()
            if (callbacks == null) {
                println("   ⚠️ ImageQualityProcessor callbacks not set, defaulting to true")
                return true
            }

            var shouldProcess = true
            callbacks.shouldProcess(imagePath, quality) { result ->
                shouldProcess = result
                println("   ${if (result) "✅" else "⏭️"} Processing ${if (result) "needed" else "can be skipped"}")
            }

            shouldProcess
        } catch (e: Exception) {
            println("   ⚠️ Exception checking dimensions: ${e.message}, defaulting to true")
            true // Process by default if unable to determine
        }
    }

    /**
     * Get image dimensions without loading full image
     *
     * Uses efficient dimension-only reading to avoid memory overhead.
     *
     * @param imagePath Path to image
     * @return Pair of (width, height) or null if unable to read
     */
    fun getImageDimensions(imagePath: String): Pair<Int, Int>? {
        return try {
            val callbacks = IOSCallbackBridge.getImageQualityProcessorCallbacks()
            if (callbacks == null) {
                println("   ⚠️ ImageQualityProcessor callbacks not set!")
                return null
            }

            var dimensions: Pair<Int, Int>? = null
            callbacks.getImageDimensions(imagePath) { width, height ->
                dimensions = Pair(width, height)
            }

            dimensions
        } catch (e: Exception) {
            println("   ⚠️ Exception getting dimensions: ${e.message}")
            null
        }
    }

    /**
     * Get JPEG quality value for scan quality preset
     *
     * Used by Swift implementation to apply consistent compression.
     *
     * @param quality Scan quality preset
     * @return JPEG quality value (75-90)
     */
    fun getJpegQuality(quality: ScanQuality): Int {
        return when (quality) {
            ScanQuality.SMALL -> JPEG_QUALITY_SMALL
            ScanQuality.STANDARD -> JPEG_QUALITY_STANDARD
            ScanQuality.LARGE -> JPEG_QUALITY_LARGE
            ScanQuality.HD -> JPEG_QUALITY_HD
        }
    }
}
