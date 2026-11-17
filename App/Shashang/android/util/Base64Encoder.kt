package com.kashyap.wizlyai.util

import android.util.Base64

/**
 * Android implementation of Base64Encoder
 *
 * Uses Android's native Base64 utility which is optimized
 * for performance and memory efficiency.
 */
actual object Base64Encoder {
    /**
     * Encode byte array to Base64 string using Android Base64 utility
     *
     * @param bytes Raw bytes to encode
     * @return Base64-encoded string with NO_WRAP flag (no line breaks)
     */
    actual fun encode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
