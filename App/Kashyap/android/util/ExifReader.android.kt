package com.kashyap.wizlyai.util

import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Android implementation of EXIF reader using androidx.exifinterface
 */
actual class ExifReader {
    /**
     * Get the orientation of an image in degrees from its EXIF data
     *
     * @param filePath Absolute path to the image file
     * @return Rotation in degrees (0, 90, 180, or 270)
     */
    actual fun getOrientation(filePath: String): Int {
        return try {
            // Verify file exists
            val file = File(filePath)
            if (!file.exists()) {
                println("⚠️ ExifReader: File does not exist - $filePath")
                return 0
            }

            // Read EXIF data
            val exif = ExifInterface(filePath)
            val orientation =
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )

            // Convert EXIF orientation to degrees
            val degrees =
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 // Treat as normal (no rotation)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 // Treat as 180° rotation
                    ExifInterface.ORIENTATION_TRANSPOSE -> 90 // Treat as 90° rotation
                    ExifInterface.ORIENTATION_TRANSVERSE -> 270 // Treat as 270° rotation
                    else -> 0
                }

            println("📷 ExifReader: Orientation=$orientation → $degrees° (file: ${file.name})")
            degrees
        } catch (e: Exception) {
            println("❌ ExifReader: Failed to read EXIF - ${e.message}")
            e.printStackTrace()
            0 // Default to no rotation on error
        }
    }
}
