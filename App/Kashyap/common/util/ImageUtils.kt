package com.kashyap.wizlyai.util

/**
 * Common image utilities interface
 */
expect object ImageUtils {
    /**
     * Read image file and convert to ByteArray for upload
     * @param filePath Absolute path to image file
     * @return ByteArray of image data, or null if failed
     */
    fun fileToByteArray(filePath: String): ByteArray?

    /**
     * Get file size in bytes
     * @param filePath Absolute path to file
     * @return File size in bytes, or 0 if failed
     */
    fun getFileSize(filePath: String): Long

    /**
     * Delete file at path
     * @param filePath Absolute path to file
     * @return true if deleted successfully
     */
    fun deleteFile(filePath: String): Boolean
}

/**
 * Convert image file to base64 string
 * Convenience method that combines fileToByteArray + Base64 encoding
 *
 * @param filePath Absolute path to image file
 * @return Base64-encoded image string
 * @throws IllegalArgumentException if file cannot be read
 */
fun convertImageToBase64(filePath: String): String {
    val imageBytes =
        ImageUtils.fileToByteArray(filePath)
            ?: throw IllegalArgumentException("Failed to read image at: $filePath")

    return Base64Encoder.encode(imageBytes)
}
