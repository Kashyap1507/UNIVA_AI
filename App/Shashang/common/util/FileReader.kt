package com.kashyap.wizlyai.util

/**
 * Platform-agnostic file reading utility
 * Uses expect/actual pattern to read files on different platforms
 */
expect class FileReader() {
    /**
     * Read bytes from a file path
     * @param path File path (may include file:// prefix which will be removed)
     * @return Byte array of file contents, or null if file doesn't exist or can't be read
     */
    suspend fun readBytes(path: String): ByteArray?

    /**
     * Check if a file exists at the given path
     * @param path File path (may include file:// prefix which will be removed)
     * @return true if file exists, false otherwise
     */
    fun exists(path: String): Boolean
}

/**
 * Singleton instance for convenience
 */
expect val fileReader: FileReader
