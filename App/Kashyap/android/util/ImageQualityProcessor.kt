package com.kashyap.wizlyai.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.kashyap.wizlyai.data.models.ScanQuality
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Image Quality Processor for Document Scanning
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
        private const val JPEG_QUALITY_SMALL = 75 // ~0.5 MB target
        private const val JPEG_QUALITY_STANDARD = 80 // ~1.5 MB target
        private const val JPEG_QUALITY_LARGE = 85 // ~3.0 MB target
        private const val JPEG_QUALITY_HD = 90 // ~5.0 MB target

        // Max dimension to load original image (prevent OOM)
        // We'll downsample large images during decode
        private const val MAX_DECODE_SIZE = 4096
    }

    /**
     * Process captured image with quality preset
     *
     * Reads the input image, applies quality preset (resize + compress),
     * and saves to output path.
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
        return try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                return Result.failure(IOException("Input file not found: $inputPath"))
            }

            // Get EXIF orientation before processing
            val exif = ExifInterface(inputPath)
            val orientation =
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )

            // Decode bitmap with efficient sampling
            val bitmap =
                decodeBitmap(inputPath, quality) ?: return Result.failure(
                    IOException("Failed to decode image: $inputPath"),
                )

            // Rotate bitmap if needed based on EXIF
            val rotatedBitmap = rotateBitmap(bitmap, orientation)

            // Resize to quality preset dimensions
            val resizedBitmap = resizeToQuality(rotatedBitmap, quality)

            // Compress and save
            val targetPath = outputPath ?: inputPath
            val success = saveBitmap(resizedBitmap, targetPath, quality)

            // Clean up bitmaps
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
            resizedBitmap.recycle()

            if (success) {
                Result.success(targetPath)
            } else {
                Result.failure(IOException("Failed to save processed image"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decode bitmap with efficient memory usage
     *
     * Uses inSampleSize to downsample large images during decode,
     * preventing OutOfMemoryError on high-resolution captures.
     *
     * @param path Image file path
     * @param quality Target quality (determines max dimensions)
     * @return Decoded Bitmap or null on failure
     */
    private fun decodeBitmap(
        path: String,
        quality: ScanQuality,
    ): Bitmap? {
        // First decode with inJustDecodeBounds=true to get dimensions
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(path, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        // Calculate appropriate sample size
        // Don't decode larger than we need for the target quality
        val maxDimension = maxOf(quality.width, quality.height)
        var sampleSize = 1

        while (originalWidth / sampleSize > maxDimension * 2 ||
            originalHeight / sampleSize > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        // Now decode with inSampleSize for memory efficiency
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory for documents

        return BitmapFactory.decodeFile(path, options)
    }

    /**
     * Rotate bitmap based on EXIF orientation
     *
     * Cameras often save images rotated and store the rotation in EXIF.
     * This ensures the image is properly oriented.
     *
     * @param bitmap Original bitmap
     * @param orientation EXIF orientation tag value
     * @return Rotated bitmap (or original if no rotation needed)
     */
    private fun rotateBitmap(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap // No rotation needed
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Resize bitmap to quality preset dimensions
     *
     * Maintains aspect ratio while fitting within preset bounds.
     * Uses bilinear filtering for smooth results.
     *
     * @param bitmap Input bitmap
     * @param quality Target quality preset
     * @return Resized bitmap
     */
    private fun resizeToQuality(
        bitmap: Bitmap,
        quality: ScanQuality,
    ): Bitmap {
        val currentWidth = bitmap.width
        val currentHeight = bitmap.height
        val targetWidth = quality.width
        val targetHeight = quality.height

        // Calculate scale to fit within target dimensions while maintaining aspect ratio
        val widthScale = targetWidth.toFloat() / currentWidth
        val heightScale = targetHeight.toFloat() / currentHeight
        val scale = minOf(widthScale, heightScale)

        // If image is already smaller than target, don't upscale
        if (scale >= 1.0f) {
            return bitmap
        }

        val newWidth = (currentWidth * scale).toInt()
        val newHeight = (currentHeight * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Save bitmap to file with quality-specific compression
     *
     * Uses JPEG format with quality preset compression levels.
     *
     * @param bitmap Bitmap to save
     * @param path Output file path
     * @param quality Scan quality (determines JPEG compression level)
     * @return True if save successful, false otherwise
     */
    private fun saveBitmap(
        bitmap: Bitmap,
        path: String,
        quality: ScanQuality,
    ): Boolean {
        return try {
            val jpegQuality =
                when (quality) {
                    ScanQuality.SMALL -> JPEG_QUALITY_SMALL
                    ScanQuality.STANDARD -> JPEG_QUALITY_STANDARD
                    ScanQuality.LARGE -> JPEG_QUALITY_LARGE
                    ScanQuality.HD -> JPEG_QUALITY_HD
                }

            FileOutputStream(path).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
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
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(imagePath, options)

            val width = options.outWidth
            val height = options.outHeight

            // Process if image is larger than target quality
            width > quality.width || height > quality.height
        } catch (e: Exception) {
            true // Process by default if unable to determine
        }
    }
}
