package com.kashyap.wizlyai.util

import java.io.File

/**
 * Android implementation of FileReader using java.io.File
 */
actual class FileReader actual constructor() {
    actual suspend fun readBytes(path: String): ByteArray? {
        return try {
            val cleanPath = path.removePrefix("file://")
            val file = File(cleanPath)
            if (file.exists()) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ Failed to read file: ${e.message}")
            null
        }
    }

    actual fun exists(path: String): Boolean {
        return try {
            val cleanPath = path.removePrefix("file://")
            File(cleanPath).exists()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Singleton instance for convenience
 */
actual val fileReader: FileReader = FileReader()
