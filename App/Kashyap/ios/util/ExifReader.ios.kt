package com.kashyap.wizlyai.util

import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS implementation of EXIF reader using callback bridge to Swift
 */
actual class ExifReader {
    /**
     * Get the orientation of an image in degrees from its EXIF data
     *
     * @param filePath Absolute path to the image file
     * @return Rotation in degrees (0, 90, 180, or 270)
     */
    actual fun getOrientation(filePath: String): Int {
        val callbacks = IOSCallbackBridge.getExifReaderCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS ExifReader: Callbacks not set, returning 0")
            return 0
        }
        return callbacks.getOrientation(filePath)
    }
}
