package com.kashyap.wizlyai.util

/**
 * Platform-specific Base64 encoder
 *
 * This uses Kotlin Multiplatform's expect/actual pattern to provide
 * platform-specific implementations of Base64 encoding.
 *
 * Usage:
 * ```kotlin
 * val imageBytes = File("photo.jpg").readBytes()
 * val base64String = Base64Encoder.encode(imageBytes)
 * ```
 */
expect object Base64Encoder {
    /**
     * Encode byte array to Base64 string
     *
     * @param bytes Raw bytes to encode
     * @return Base64-encoded string (no line breaks)
     */
    fun encode(bytes: ByteArray): String
}
