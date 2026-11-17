package com.kashyap.wizlyai.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Android implementation of DirectUploadService
 *
 * Uses OkHttp for efficient HTTP PUT uploads with:
 * - Progress tracking
 * - Streaming uploads (no memory buffering for large files)
 * - Connection pooling
 * - Automatic retries
 */
actual class DirectUploadService actual constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS) // 5 min for large uploads
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Upload file data directly to Firebase Storage using a signed URL
     */
    actual suspend fun uploadToSignedUrl(
        signedUrl: String,
        fileData: ByteArray,
        contentType: String,
        headers: Map<String, String>,
        progressCallback: ((Float) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("📤 [DirectUpload] Starting upload to signed URL (${fileData.size / 1024}KB)")

            val mediaType = contentType.toMediaType()

            // Create request body with progress tracking
            val requestBody = if (progressCallback != null) {
                ProgressRequestBody(fileData, mediaType.toString(), progressCallback)
            } else {
                fileData.toRequestBody(mediaType)
            }

            val requestBuilder = Request.Builder()
                .url(signedUrl)
                .put(requestBody)
                .header("Content-Type", contentType)

            // Add custom headers
            headers.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            val request = requestBuilder.build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                println("✅ [DirectUpload] Upload successful: ${response.code}")
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                println("❌ [DirectUpload] Upload failed: ${response.code} - $errorBody")
                Result.failure(IOException("Upload failed: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            println("❌ [DirectUpload] Exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Upload file from path directly to Firebase Storage
     */
    actual suspend fun uploadFileToSignedUrl(
        signedUrl: String,
        filePath: String,
        contentType: String,
        headers: Map<String, String>,
        progressCallback: ((Float) -> Unit)?
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(IOException("File not found: $filePath"))
            }

            val fileSize = file.length()
            println("📤 [DirectUpload] Starting file upload: ${file.name} (${fileSize / 1024}KB)")

            val mediaType = contentType.toMediaType()

            // Create streaming request body with progress
            val requestBody = if (progressCallback != null) {
                StreamingProgressRequestBody(file, mediaType.toString(), progressCallback)
            } else {
                file.readBytes().toRequestBody(mediaType)
            }

            val requestBuilder = Request.Builder()
                .url(signedUrl)
                .put(requestBody)
                .header("Content-Type", contentType)

            headers.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            val request = requestBuilder.build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                println("✅ [DirectUpload] File upload successful: ${response.code}")
                Result.success(fileSize)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                println("❌ [DirectUpload] File upload failed: ${response.code} - $errorBody")
                Result.failure(IOException("Upload failed: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            println("❌ [DirectUpload] Exception: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * OkHttp RequestBody with progress tracking for byte arrays
 */
private class ProgressRequestBody(
    private val data: ByteArray,
    private val contentType: String,
    private val progressCallback: (Float) -> Unit
) : okhttp3.RequestBody() {

    override fun contentType() = contentType.toMediaType()

    override fun contentLength() = data.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = data.size.toLong()
        var uploaded = 0L
        val bufferSize = 8192 // 8KB chunks

        var offset = 0
        while (offset < data.size) {
            val chunkSize = minOf(bufferSize, data.size - offset)
            sink.write(data, offset, chunkSize)
            offset += chunkSize
            uploaded += chunkSize

            // Report progress
            val progress = uploaded.toFloat() / total.toFloat()
            progressCallback(progress)
        }
    }
}

/**
 * OkHttp RequestBody with streaming progress for files
 */
private class StreamingProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val progressCallback: (Float) -> Unit
) : okhttp3.RequestBody() {

    override fun contentType() = contentType.toMediaType()

    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var uploaded = 0L
        val bufferSize = 8192L // 8KB chunks

        file.source().use { source ->
            var bytesRead: Long
            while (source.read(sink.buffer, bufferSize).also { bytesRead = it } != -1L) {
                sink.emit()
                uploaded += bytesRead

                // Report progress
                val progress = uploaded.toFloat() / total.toFloat()
                progressCallback(progress)
            }
        }
    }
}
