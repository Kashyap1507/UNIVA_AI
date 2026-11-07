package com.kashyap.wizlyai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.max

/**
 * iOS implementation of ImageQualityOptimizer
 */
@OptIn(ExperimentalForeignApi::class)
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
            val image = UIImage.imageWithContentsOfFile(inputPath) ?: return null
            val (width, height) =
                image.size.useContents {
                    width.toInt() to height.toInt()
                }

            if (max(width, height) <= maxDimension) {
                // No downsampling needed, just copy
                val imageData = UIImageJPEGRepresentation(image, 0.9)
                imageData?.writeToFile(outputPath, true)
                return outputPath
            }

            // Calculate new size
            val scale = maxDimension.toDouble() / max(width, height).toDouble()
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()

            // Resize using UIImage
            val newSize = platform.CoreGraphics.CGSizeMake(newWidth.toDouble(), newHeight.toDouble())
            platform.UIKit.UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
            image.drawInRect(platform.CoreGraphics.CGRectMake(0.0, 0.0, newWidth.toDouble(), newHeight.toDouble()))
            val resizedImage = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
            platform.UIKit.UIGraphicsEndImageContext()

            if (resizedImage != null) {
                val imageData = UIImageJPEGRepresentation(resizedImage, 0.9)
                imageData?.writeToFile(outputPath, true)
                outputPath
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error downsampling image: ${e.message}")
            null
        }
    }

    /**
     * Calculate optimal JPEG quality
     * Simplified for iOS - returns balanced quality
     */
    actual fun calculateOptimalQuality(imagePath: String): Int {
        // Simplified implementation - could be enhanced with Core Image filters
        return ImageQualityConstants.BALANCED_QUALITY_JPEG
    }

    /**
     * Apply sharpening filter
     * Simplified for iOS - copies image without sharpening
     */
    actual fun sharpenImage(
        inputPath: String,
        outputPath: String,
        strength: Float,
    ): String? {
        return try {
            // Simplified - copy image (sharpening could be added with Core Image filters)
            val image = UIImage.imageWithContentsOfFile(inputPath) ?: return null
            val imageData = UIImageJPEGRepresentation(image, 0.9)
            imageData?.writeToFile(outputPath, true)
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
            val image = UIImage.imageWithContentsOfFile(inputPath) ?: return null

            var quality = 0.9
            var imageData: NSData? = null
            var currentSize = Long.MAX_VALUE

            while (quality > 0.5 && currentSize > targetSizeBytes) {
                imageData = UIImageJPEGRepresentation(image, quality)
                currentSize = imageData?.length?.toLong() ?: Long.MAX_VALUE
                quality -= 0.05
            }

            if (currentSize <= targetSizeBytes && imageData != null) {
                imageData.writeToFile(outputPath, true)
                outputPath
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error compressing image: ${e.message}")
            null
        }
    }

    /**
     * Optimize image for upload
     */
    actual fun optimizeForUpload(
        inputPath: String,
        outputPath: String,
        settings: ImageQualitySettings,
    ): String? {
        return try {
            // Downsample
            val downsampled =
                downsampleImage(inputPath, outputPath, settings.maxDimension)
                    ?: return null

            // Compress to quality
            val image = UIImage.imageWithContentsOfFile(downsampled) ?: return null
            val quality = settings.jpegQuality.toDouble() / 100.0
            val imageData = UIImageJPEGRepresentation(image, quality)
            imageData?.writeToFile(outputPath, true)

            val fileSize = imageData?.length?.toLong() ?: 0
            println("Final image size: ${fileSize / 1024} KB")

            outputPath
        } catch (e: Exception) {
            println("Error optimizing image: ${e.message}")
            null
        }
    }
}
