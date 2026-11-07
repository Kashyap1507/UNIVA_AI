package com.kashyap.wizlyai.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Android implementation of ImageQualityOptimizer
 */
actual class ImageQualityOptimizer {
    /**
     * Downsample large image
     */
    actual fun downsampleImage(
        inputPath: String,
        outputPath: String,
        maxDimension: Int,
    ): String? {
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(inputPath, options)

            val width = options.outWidth
            val height = options.outHeight

            // Calculate sample size
            var sampleSize = 1
            if (width > maxDimension || height > maxDimension) {
                val maxOriginalDim = max(width, height)
                sampleSize = (maxOriginalDim.toFloat() / maxDimension.toFloat()).toInt()
            }

            // Load downsampled bitmap
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            val bitmap = BitmapFactory.decodeFile(inputPath, options) ?: return null

            // Further scale if needed (inSampleSize is power of 2)
            val scaledBitmap =
                if (max(bitmap.width, bitmap.height) > maxDimension) {
                    val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height).toFloat()
                    val newWidth = (bitmap.width * scale).toInt()
                    val newHeight = (bitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                        bitmap.recycle()
                    }
                } else {
                    bitmap
                }

            // Save with high quality
            FileOutputStream(outputPath).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scaledBitmap.recycle()

            outputPath
        } catch (e: Exception) {
            println("Error downsampling image: ${e.message}")
            null
        }
    }

    /**
     * Calculate optimal JPEG quality based on content
     */
    actual fun calculateOptimalQuality(imagePath: String): Int {
        return try {
            // Load downsampled version for analysis
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 4 // Use 1/4 size for analysis
                }
            val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return ImageQualityConstants.BALANCED_QUALITY_JPEG

            // Simple edge detection: count high-contrast pixels
            val width = bitmap.width
            val height = bitmap.height
            var edgeCount = 0
            val totalPixels = (width - 1) * (height - 1)

            for (y in 0 until height - 1) {
                for (x in 0 until width - 1) {
                    val pixel = bitmap.getPixel(x, y)
                    val nextPixel = bitmap.getPixel(x + 1, y)

                    // Calculate luminance difference
                    val r1 = (pixel shr 16) and 0xFF
                    val g1 = (pixel shr 8) and 0xFF
                    val b1 = pixel and 0xFF
                    val lum1 = (0.299 * r1 + 0.587 * g1 + 0.114 * b1).toInt()

                    val r2 = (nextPixel shr 16) and 0xFF
                    val g2 = (nextPixel shr 8) and 0xFF
                    val b2 = nextPixel and 0xFF
                    val lum2 = (0.299 * r2 + 0.587 * g2 + 0.114 * b2).toInt()

                    val diff = kotlin.math.abs(lum1 - lum2)
                    if (diff > 30) { // Threshold for edge
                        edgeCount++
                    }
                }
            }

            bitmap.recycle()

            val edgeRatio = edgeCount.toFloat() / totalPixels.toFloat()

            when {
                edgeRatio > ImageQualityConstants.EDGE_DETECTION_THRESHOLD_HIGH -> ImageQualityConstants.TEXT_QUALITY_JPEG
                edgeRatio < ImageQualityConstants.EDGE_DETECTION_THRESHOLD_LOW -> ImageQualityConstants.PHOTO_QUALITY_JPEG
                else -> ImageQualityConstants.BALANCED_QUALITY_JPEG
            }
        } catch (e: Exception) {
            println("Error analyzing image quality: ${e.message}")
            ImageQualityConstants.BALANCED_QUALITY_JPEG
        }
    }

    /**
     * Apply sharpening filter
     */
    actual fun sharpenImage(
        inputPath: String,
        outputPath: String,
        strength: Float,
    ): String? {
        return try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return null

            // Simple unsharp mask approximation
            val sharpenedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)

            for (y in 1 until bitmap.height - 1) {
                for (x in 1 until bitmap.width - 1) {
                    val center = bitmap.getPixel(x, y)

                    // 3x3 kernel average (blur)
                    var r = 0
                    var g = 0
                    var b = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val pixel = bitmap.getPixel(x + dx, y + dy)
                            r += (pixel shr 16) and 0xFF
                            g += (pixel shr 8) and 0xFF
                            b += pixel and 0xFF
                        }
                    }
                    r /= 9
                    g /= 9
                    b /= 9

                    // Unsharp mask: original + (original - blur) * strength
                    val cr = (center shr 16) and 0xFF
                    val cg = (center shr 8) and 0xFF
                    val cb = center and 0xFF

                    val nr = (cr + ((cr - r) * strength)).toInt().coerceIn(0, 255)
                    val ng = (cg + ((cg - g) * strength)).toInt().coerceIn(0, 255)
                    val nb = (cb + ((cb - b) * strength)).toInt().coerceIn(0, 255)

                    val sharpened = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                    sharpenedBitmap.setPixel(x, y, sharpened)
                }
            }

            bitmap.recycle()

            // Save
            FileOutputStream(outputPath).use { out ->
                sharpenedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            sharpenedBitmap.recycle()

            outputPath
        } catch (e: Exception) {
            println("Error sharpening image: ${e.message}")
            null
        }
    }

    /**
     * Compress to target file size
     */
    actual fun compressToSize(
        inputPath: String,
        outputPath: String,
        targetSizeBytes: Long,
    ): String? {
        return try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return null

            var quality = 90
            var currentSize = Long.MAX_VALUE

            while (quality > 50 && currentSize > targetSizeBytes) {
                FileOutputStream(outputPath).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                currentSize = File(outputPath).length()
                quality -= 5
            }

            bitmap.recycle()

            if (currentSize <= targetSizeBytes) outputPath else null
        } catch (e: Exception) {
            println("Error compressing image: ${e.message}")
            null
        }
    }

    /**
     * Optimize image for upload (all optimizations)
     */
    actual fun optimizeForUpload(
        inputPath: String,
        outputPath: String,
        settings: ImageQualitySettings,
    ): String? {
        return try {
            val tempPath1 = "$outputPath.temp1.jpg"
            val tempPath2 = "$outputPath.temp2.jpg"

            // Step 1: Downsample if needed
            val downsampledPath =
                downsampleImage(inputPath, tempPath1, settings.maxDimension)
                    ?: return null

            // Step 2: Calculate optimal quality
            val optimalQuality = min(settings.jpegQuality, calculateOptimalQuality(downsampledPath))
            println("Optimal JPEG quality: $optimalQuality")

            // Step 3: Apply sharpening if enabled
            val sharpenedPath =
                if (settings.enableSharpening) {
                    sharpenImage(downsampledPath, tempPath2, 0.3f) ?: downsampledPath
                } else {
                    downsampledPath
                }

            // Step 4: Compress to final quality
            val bitmap = BitmapFactory.decodeFile(sharpenedPath) ?: return null
            FileOutputStream(outputPath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, optimalQuality, out)
            }
            bitmap.recycle()

            // Cleanup temp files
            File(tempPath1).delete()
            File(tempPath2).delete()

            val finalSize = File(outputPath).length()
            println("Final image size: ${finalSize / 1024} KB")

            outputPath
        } catch (e: Exception) {
            println("Error optimizing image: ${e.message}")
            null
        }
    }
}
