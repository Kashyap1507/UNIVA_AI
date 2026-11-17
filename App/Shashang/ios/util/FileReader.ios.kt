package com.kashyap.wizlyai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/**
 * iOS implementation of FileReader
 * Uses NSFileManager.contentsAtPath() to read file bytes
 */
@OptIn(ExperimentalForeignApi::class)
actual class FileReader actual constructor() {
    /**
     * Read bytes from a file path using iOS Foundation APIs
     * @param path File path (may include file:// prefix which will be removed)
     * @return Byte array of file contents, or null if file doesn't exist or can't be read
     */
    actual suspend fun readBytes(path: String): ByteArray? {
        return try {
            val cleanPath = path.removePrefix("file://")
            val fileManager = NSFileManager.defaultManager
            val data = fileManager.contentsAtPath(cleanPath) ?: return null

            // Convert NSData to ByteArray using C-interop memcpy
            val byteArray = ByteArray(data.length.toInt())
            byteArray.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }

            println("✅ iOS: FileReader.readBytes() - Read ${byteArray.size} bytes from $cleanPath")
            byteArray
        } catch (e: Exception) {
            println("❌ iOS: FileReader.readBytes() - Failed to read file: ${e.message}")
            null
        }
    }

    actual fun exists(path: String): Boolean {
        return try {
            val cleanPath = path.removePrefix("file://")
            NSFileManager.defaultManager.fileExistsAtPath(cleanPath)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Singleton instance for convenience
 */
actual val fileReader: FileReader = FileReader()
