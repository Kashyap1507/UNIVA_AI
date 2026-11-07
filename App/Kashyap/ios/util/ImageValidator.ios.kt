package com.kashyap.wizlyai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSFileManager
import platform.UIKit.UIImage

/**
 * iOS implementation of ImageValidator
 */
@OptIn(ExperimentalForeignApi::class)
actual class ImageValidator {
    /**
     * Validate image file on iOS
     */
    actual fun validateImage(filePath: String): ImageValidationResult {
        val fileManager = NSFileManager.defaultManager

        // Check file exists
        if (!fileManager.fileExistsAtPath(filePath)) {
            return ImageValidationResult.NotFound
        }

        // Check file size
        val fileSize = getFileSize(filePath)
        if (fileSize < 0) {
            return ImageValidationResult.NotFound
        }
        if (fileSize > ImageValidationConstants.MAX_FILE_SIZE_BYTES) {
            return ImageValidationResult.TooLarge
        }
        if (fileSize < ImageValidationConstants.MIN_FILE_SIZE_BYTES) {
            return ImageValidationResult.TooSmall
        }

        // Check format
        if (!isSupportedFormat(filePath)) {
            return ImageValidationResult.UnsupportedFormat
        }

        // Try to load image and get dimensions
        return try {
            val image = UIImage.imageWithContentsOfFile(filePath)
            if (image == null) {
                ImageValidationResult.Corrupted
            } else {
                val (width, height) =
                    image.size.useContents {
                        width.toInt() to height.toInt()
                    }

                if (width <= 0 || height <= 0) {
                    ImageValidationResult.Corrupted
                } else if (width < ImageValidationConstants.MIN_DIMENSION ||
                    height < ImageValidationConstants.MIN_DIMENSION
                ) {
                    ImageValidationResult.TooSmall
                } else if (width > ImageValidationConstants.MAX_DIMENSION ||
                    height > ImageValidationConstants.MAX_DIMENSION
                ) {
                    ImageValidationResult.Error("Image dimensions too large (max ${ImageValidationConstants.MAX_DIMENSION}px)")
                } else {
                    ImageValidationResult.Valid(width, height, fileSize)
                }
            }
        } catch (e: Exception) {
            ImageValidationResult.Corrupted
        }
    }

    /**
     * Check if file format is supported
     */
    actual fun isSupportedFormat(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension !in ImageValidationConstants.SUPPORTED_EXTENSIONS) {
            return false
        }

        // Try to load with UIImage - if it loads, it's a valid format
        return try {
            val image = UIImage.imageWithContentsOfFile(filePath)
            image != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get file size in bytes
     */
    actual fun getFileSize(filePath: String): Long {
        return try {
            val fileManager = NSFileManager.defaultManager
            val attributes = fileManager.attributesOfItemAtPath(filePath, null)
            val fileSize = attributes?.get(platform.Foundation.NSFileSize) as? Long ?: -1L
            fileSize
        } catch (e: Exception) {
            -1L
        }
    }
}
