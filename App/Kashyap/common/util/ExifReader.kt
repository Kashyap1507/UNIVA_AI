package com.kashyap.wizlyai.util

/**
 * Platform-agnostic EXIF metadata reader
 *
 * Reads EXIF orientation data from images to ensure proper display orientation,
 * especially important for gallery-imported photos which may have orientation metadata.
 */
expect class ExifReader {
    /**
     * Get the orientation of an image in degrees from its EXIF data
     *
     * @param filePath Absolute path to the image file
     * @return Rotation in degrees (0, 90, 180, or 270)
     */
    fun getOrientation(filePath: String): Int
}

/**
 * EXIF orientation constants matching standard EXIF values
 */
object ExifOrientation {
    const val ORIENTATION_NORMAL = 1 // 0 degrees
    const val ORIENTATION_ROTATE_90 = 6 // 90 degrees CW
    const val ORIENTATION_ROTATE_180 = 3 // 180 degrees
    const val ORIENTATION_ROTATE_270 = 8 // 270 degrees CW (90 CCW)
}
