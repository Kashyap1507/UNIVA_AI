package com.kashyap.wizlyai.util

import java.io.File

/**
 * Android implementation of ImageUtils
 */
actual object ImageUtils {
    actual fun fileToByteArray(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    actual fun getFileSize(filePath: String): Long {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.length()
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    actual fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.exists() && file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
