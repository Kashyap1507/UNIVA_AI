package com.kashyap.wizlyai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64EncodingOptions
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithBytes

/**
 * iOS implementation of Base64Encoder
 *
 * Uses iOS Foundation's NSData.base64EncodedString which is
 * optimized for performance on iOS devices.
 */
@OptIn(ExperimentalForeignApi::class)
actual object Base64Encoder {
    /**
     * Encode byte array to Base64 string using iOS Foundation NSData
     *
     * @param bytes Raw bytes to encode
     * @return Base64-encoded string (no line breaks)
     */
    actual fun encode(bytes: ByteArray): String {
        return bytes.usePinned { pinned ->
            val nsData =
                NSData.dataWithBytes(
                    bytes = pinned.addressOf(0),
                    length = bytes.size.toULong(),
                )
            nsData.base64EncodedStringWithOptions(NSDataBase64EncodingOptions.MIN_VALUE)
        }
    }
}
