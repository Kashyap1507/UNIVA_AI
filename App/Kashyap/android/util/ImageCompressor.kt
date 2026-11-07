package com.kashyap.wizlyai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Enhanced image compression utility (UX-013)
 * Optimizes images for storage and upload while maintaining text legibility
 * Features:
 * - Adaptive quality based on content (90% for text, 85% for photos)
 * - Smart downsampling to 2048x2048 max (OCR-friendly)
 * - Sharpening filter for text clarity
 * - EXIF orientation preservation
 * - Target: 200-500KB per image
 */
class ImageCompressor(private val context: Context) {
    /**
     * Compress image for storage
     * Quality: 80%, Max resolution: 1920x1920
     * Good balance between file size and quality
     */
    suspend fun compressForStorage(
        sourceFile: File,
        outputFile: File,
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val compressedFile =
                    Compressor.compress(context, sourceFile) {
                        resolution(1920, 1920) // Max 1920px on longest side
                        quality(80) // 80% JPEG quality
                        format(Bitmap.CompressFormat.JPEG)
                        size(2_097_152) // Max 2MB file size
                    }

                // Copy to final output location
                compressedFile.copyTo(outputFile, overwrite = true)
                Result.success(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Compress image for upload
     * More aggressive compression for bandwidth optimization
     * Quality: 75%, Max resolution: 1920x1920
     */
    suspend fun compressForUpload(
        sourceFile: File,
        outputFile: File,
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val compressedFile =
                    Compressor.compress(context, sourceFile) {
                        resolution(1920, 1920)
                        quality(75) // Slightly lower quality for upload
                        format(Bitmap.CompressFormat.JPEG)
                        size(1_572_864) // Max 1.5MB
                    }

                compressedFile.copyTo(outputFile, overwrite = true)
                Result.success(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Generate thumbnail
     * Quality: 75%, Max resolution: 300x300
     * Small file size for fast loading in lists
     */
    suspend fun generateThumbnail(
        sourceFile: File,
        outputFile: File,
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val compressedFile =
                    Compressor.compress(context, sourceFile) {
                        resolution(300, 300) // Small thumbnail
                        quality(75)
                        format(Bitmap.CompressFormat.JPEG)
                        size(102_400) // Max 100KB for thumbnails
                    }

                compressedFile.copyTo(outputFile, overwrite = true)
                Result.success(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Get image dimensions without loading full image into memory
     */
    fun getImageDimensions(file: File): Pair<Int, Int>? {
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, options)
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get file size in bytes
     */
    fun getFileSize(file: File): Long {
        return try {
            file.length()
        } catch (e: Exception) {
            0L
        }
    }

    // ========== Enhanced Compression (UX-013) ==========

    /**
     * Compress image for document scanning (UX-013)
     * - Adaptive quality: 90% for text-heavy, 85% for photos
     * - Max resolution: 2048x2048 (OCR-friendly)
     * - Sharpening filter for text clarity
     * - EXIF orientation preservation
     * - Target: 200-500KB
     */
    suspend fun compressForDocumentScan(
        sourceFile: File,
        outputFile: File,
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                println("🗜️ [ImageCompressor] Starting document scan compression")
                println("📊 Source file size: ${sourceFile.length() / 1024}KB")

                // Read EXIF orientation before processing
                val exif = ExifInterface(sourceFile.absolutePath)
                val orientation =
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )

                // Load bitmap with downsampling if needed
                val bitmap =
                    loadBitmap(sourceFile, MAX_DIMENSION_OCR)
                        ?: return@withContext Result.failure(Exception("Failed to load bitmap"))

                // Apply EXIF orientation
                val orientedBitmap = applyExifOrientation(bitmap, orientation)

                // Detect if image is text-heavy
                val isTextHeavy = detectTextHeavyContent(orientedBitmap)
                val quality = if (isTextHeavy) QUALITY_TEXT_HEAVY else QUALITY_PHOTO

                println("📝 Text-heavy detection: $isTextHeavy (quality: $quality%)")

                // Apply sharpening for text clarity
                val sharpenedBitmap =
                    if (isTextHeavy) {
                        sharpenBitmap(orientedBitmap)
                    } else {
                        orientedBitmap
                    }

                // Compress to JPEG
                FileOutputStream(outputFile).use { out ->
                    sharpenedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                // Preserve EXIF orientation in output
                val outputExif = ExifInterface(outputFile.absolutePath)
                outputExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    orientation.toString(),
                )
                outputExif.saveAttributes()

                // Clean up bitmaps
                if (sharpenedBitmap != orientedBitmap) sharpenedBitmap.recycle()
                if (orientedBitmap != bitmap) orientedBitmap.recycle()
                bitmap.recycle()

                val outputSize = outputFile.length()
                println("✅ Compressed: ${outputSize / 1024}KB (${(1 - outputSize.toFloat() / sourceFile.length()) * 100}% reduction)")

                Result.success(outputFile)
            } catch (e: Exception) {
                println("❌ Compression failed: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Load bitmap with smart downsampling
     * Downsamples to max dimension while maintaining aspect ratio
     */
    private fun loadBitmap(
        file: File,
        maxDimension: Int,
    ): Bitmap? {
        return try {
            // First, get dimensions without loading full bitmap
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight

            // Calculate inSampleSize
            var inSampleSize = 1
            if (width > maxDimension || height > maxDimension) {
                val halfWidth = width / 2
                val halfHeight = height / 2

                while ((halfWidth / inSampleSize) >= maxDimension &&
                    (halfHeight / inSampleSize) >= maxDimension
                ) {
                    inSampleSize *= 2
                }
            }

            // Load bitmap with calculated inSampleSize
            val loadOptions =
                BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
            val bitmap =
                BitmapFactory.decodeFile(file.absolutePath, loadOptions)
                    ?: return null

            // If still larger than max, scale down precisely
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale =
                    min(
                        maxDimension.toFloat() / bitmap.width,
                        maxDimension.toFloat() / bitmap.height,
                    )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()

                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaled != bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            println("❌ Failed to load bitmap: ${e.message}")
            null
        }
    }

    /**
     * Apply EXIF orientation to bitmap
     */
    private fun applyExifOrientation(
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
            else -> return bitmap // ORIENTATION_NORMAL or ORIENTATION_UNDEFINED
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            println("⚠️ Failed to apply EXIF orientation: ${e.message}")
            bitmap
        }
    }

    /**
     * Detect if image contains primarily text (high edge density)
     * Uses simple edge detection heuristic
     */
    private fun detectTextHeavyContent(bitmap: Bitmap): Boolean {
        return try {
            // Sample small version for faster processing
            val sampleSize = 200
            val sampleBitmap =
                if (bitmap.width > sampleSize || bitmap.height > sampleSize) {
                    Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
                } else {
                    bitmap
                }

            var edgePixels = 0
            val threshold = 30 // Edge detection threshold

            // Simple edge detection by comparing adjacent pixels
            for (y in 0 until sampleBitmap.height - 1) {
                for (x in 0 until sampleBitmap.width - 1) {
                    val pixel = sampleBitmap.getPixel(x, y)
                    val pixelRight = sampleBitmap.getPixel(x + 1, y)
                    val pixelDown = sampleBitmap.getPixel(x, y + 1)

                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = (r + g + b) / 3

                    val rRight = (pixelRight shr 16) and 0xFF
                    val gRight = (pixelRight shr 8) and 0xFF
                    val bRight = pixelRight and 0xFF
                    val grayRight = (rRight + gRight + bRight) / 3

                    val rDown = (pixelDown shr 16) and 0xFF
                    val gDown = (pixelDown shr 8) and 0xFF
                    val bDown = pixelDown and 0xFF
                    val grayDown = (rDown + gDown + bDown) / 3

                    val diffRight = kotlin.math.abs(gray - grayRight)
                    val diffDown = kotlin.math.abs(gray - grayDown)

                    if (diffRight > threshold || diffDown > threshold) {
                        edgePixels++
                    }
                }
            }

            if (sampleBitmap != bitmap) sampleBitmap.recycle()

            val totalPixels = sampleBitmap.width * sampleBitmap.height
            val edgeRatio = edgePixels.toFloat() / totalPixels

            // Text-heavy images typically have >10% edge pixels
            edgeRatio > 0.10f
        } catch (e: Exception) {
            println("⚠️ Text detection failed: ${e.message}")
            true // Default to text-heavy (higher quality) on error
        }
    }

    /**
     * Apply sharpening filter to enhance text clarity
     * Uses unsharp mask technique
     */
    private fun sharpenBitmap(bitmap: Bitmap): Bitmap {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val sharpened = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

            // Apply unsharp mask: original + (original - blurred) * amount
            // Simplified approach: enhance contrast slightly for text
            val sharpenAmount = 1.2f // 20% sharpening

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val center = bitmap.getPixel(x, y)

                    // Get surrounding pixels for local average
                    val top = bitmap.getPixel(x, y - 1)
                    val bottom = bitmap.getPixel(x, y + 1)
                    val left = bitmap.getPixel(x - 1, y)
                    val right = bitmap.getPixel(x + 1, y)

                    // Process each color channel
                    val r = ((center shr 16) and 0xFF)
                    val g = ((center shr 8) and 0xFF)
                    val b = (center and 0xFF)

                    // Calculate average of surrounding pixels
                    val avgR =
                        (
                            ((top shr 16) and 0xFF) + ((bottom shr 16) and 0xFF) +
                                ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF)
                        ) / 4
                    val avgG =
                        (
                            ((top shr 8) and 0xFF) + ((bottom shr 8) and 0xFF) +
                                ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)
                        ) / 4
                    val avgB =
                        (
                            (top and 0xFF) + (bottom and 0xFF) +
                                (left and 0xFF) + (right and 0xFF)
                        ) / 4

                    // Sharpen: pixel + (pixel - average) * amount
                    val newR = (r + (r - avgR) * (sharpenAmount - 1)).toInt().coerceIn(0, 255)
                    val newG = (g + (g - avgG) * (sharpenAmount - 1)).toInt().coerceIn(0, 255)
                    val newB = (b + (b - avgB) * (sharpenAmount - 1)).toInt().coerceIn(0, 255)

                    val newPixel = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
                    sharpened.setPixel(x, y, newPixel)
                }
            }

            // Copy edge pixels as-is
            for (x in 0 until width) {
                sharpened.setPixel(x, 0, bitmap.getPixel(x, 0))
                sharpened.setPixel(x, height - 1, bitmap.getPixel(x, height - 1))
            }
            for (y in 0 until height) {
                sharpened.setPixel(0, y, bitmap.getPixel(0, y))
                sharpened.setPixel(width - 1, y, bitmap.getPixel(width - 1, y))
            }

            sharpened
        } catch (e: Exception) {
            println("⚠️ Sharpening failed: ${e.message}")
            bitmap // Return original on error
        }
    }

    companion object {
        private const val MAX_DIMENSION_OCR = 2048 // Max dimension for OCR quality
        private const val QUALITY_TEXT_HEAVY = 90 // JPEG quality for text-heavy images
        private const val QUALITY_PHOTO = 85 // JPEG quality for photos
    }
}
