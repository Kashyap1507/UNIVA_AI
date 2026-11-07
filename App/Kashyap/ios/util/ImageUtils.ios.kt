package com.kashyap.wizlyai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/**
 * iOS implementation of ImageUtils
 */
@OptIn(ExperimentalForeignApi::class)
actual object ImageUtils {
    actual fun fileToByteArray(filePath: String): ByteArray? {
        return try {
            val fileManager = NSFileManager.defaultManager
            val data = fileManager.contentsAtPath(filePath) ?: return null
            val byteArray = ByteArray(data.length.toInt())
            byteArray.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            byteArray
        } catch (e: Exception) {
            println("Error reading file: ${e.message}")
            null
        }
    }

    actual fun getFileSize(filePath: String): Long {
        return try {
            val fileManager = NSFileManager.defaultManager
            val attributes = fileManager.attributesOfItemAtPath(filePath, null)
            attributes?.get("NSFileSize") as? Long ?: 0L
        } catch (e: Exception) {
            println("Error getting file size: ${e.message}")
            0L
        }
    }

    actual fun deleteFile(filePath: String): Boolean {
        return try {
            val fileManager = NSFileManager.defaultManager
            fileManager.removeItemAtPath(filePath, null)
            true
        } catch (e: Exception) {
            println("Error deleting file: ${e.message}")
            false
        }
    }
}
