package com.kashyap.wizlyai.util

import android.graphics.BitmapFactory
import java.io.File

/**
 * Android implementation of ImageValidator
 */
actual class ImageValidator {
    /**
     * Validate image file on Android
     */
    actual fun validateImage(filePath: String): ImageValidationResult {
        val file = File(filePath)

        // Check file exists
        if (!file.exists()) {
            return ImageValidationResult.NotFound
        }

        // Check file size
        val fileSize = file.length()
        if (fileSize > ImageValidationConstants.MAX_FILE_SIZE_BYTES) {
            return ImageValidationResult.TooLarge
        }
        if (fileSize < ImageValidationConstants.MIN_FILE_SIZE_BYTES) {
            return ImageValidationResult.TooSmall
        }

        // Check format via extension and magic bytes
        if (!isSupportedFormat(filePath)) {
            return ImageValidationResult.UnsupportedFormat
        }

        // Try to decode and get dimensions
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(filePath, options)

            // Check if decode succeeded
            val width = options.outWidth
            val height = options.outHeight

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
        } catch (e: Exception) {
            ImageValidationResult.Corrupted
        }
    }

    /**
     * Check if file format is supported
     */
    actual fun isSupportedFormat(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        val extension = file.extension.lowercase()
        if (extension !in ImageValidationConstants.SUPPORTED_EXTENSIONS) {
            return false
        }

        // Verify magic bytes for common formats
        return try {
            val bytes =
                file.inputStream().use { stream ->
                    ByteArray(12).also { stream.read(it) }
                }

            when {
                // JPEG: FF D8 FF
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> true
                // PNG: 89 50 4E 47
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> true
                // HEIC/HEIF: Check for ftyp box
                String(bytes.slice(4..7).toByteArray()) == "ftyp" -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get file size in bytes
     */
    actual fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else -1
    }
}
