package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.models.*
import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.util.Base64Encoder
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Repository for scanned documents and their pages (owned by the signed-in student)
 */
class DocumentRepository {
    private val client = ApiClient.httpClient
    private val json = JsonConfig.json // Use shared Json configuration with polymorphic support

    // Processing state for loading overlays
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    companion object {
        /**
         * Convert ByteArray to Base64 string
         *
         * This is used for uploading images to the backend.
         * The backend expects base64-encoded image data.
         *
         * Uses platform-specific Base64Encoder (expect/actual pattern)
         * for proper binary data encoding.
         *
         * @param bytes Image bytes
         * @return Base64-encoded string
         */
        fun encodeToBase64(bytes: ByteArray): String {
            return Base64Encoder.encode(bytes)
        }
    }

    // ========== Document CRUD ==========

    /**
     * Upload scan and create a new document with pages
     *
     * This creates a new document with one or more page images.
     * The backend expects all pages to be sent in a single request.
     *
     * Backend will:
     * - Upload images to Google Cloud Storage
     * - Generate thumbnails
     * - Create document in Firebase Firestore
     * - Return document with GCS URLs for all pages
     *
     * The server sets the owner from the JWT.
     *
     * @param pages List of pages with image data and metadata
     * @param title Document title
     * @param type Document type
     * @param courseId Optional course the scan belongs to
     * @return Result with created document containing remote URLs
     */
    suspend fun uploadScan(
        pages: List<PageUploadData>,
        title: String,
        type: DocumentType = DocumentType.HOMEWORK,
        courseId: String? = null,
    ): Result<Document> {
        return try {
            // Build serializable request
            val request =
                DocumentScanRequest(
                    title = title,
                    type = type.name,
                    course_id = courseId,
                    pages =
                        pages.map { page ->
                            PageData(
                                image_data = encodeToBase64(page.imageData),
                                page_number = page.pageNumber,
                                width = page.width,
                                height = page.height,
                            )
                        },
                )

            val response =
                client.post("/api/documents/scan") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.isSuccess()) {
                // Backend wraps response in {"success": true, "data": {...}}
                val apiResponse = json.decodeFromString<ApiResponse<Document>>(response.bodyAsText())
                if (apiResponse.success && apiResponse.data != null) {
                    Result.success(apiResponse.data)
                } else {
                    Result.failure(Exception(apiResponse.error ?: "Upload failed"))
                }
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to upload scan: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the student's scanned documents
     */
    suspend fun getSourceDocuments(type: DocumentType? = null): Result<List<Document>> {
        return try {
            val response =
                client.get("/api/documents") {
                    type?.let { parameter("type", it.name) }
                }

            if (response.status.isSuccess()) {
                // Backend wraps lists in {"success": true, "data": [...], "count": n}
                val apiResponse = json.decodeFromString<ApiResponse<List<Document>>>(response.bodyAsText())
                if (apiResponse.success) {
                    Result.success(apiResponse.data.orEmpty())
                } else {
                    Result.failure(Exception(apiResponse.error ?: "Failed to fetch documents"))
                }
            } else {
                Result.failure(Exception("Failed to fetch documents: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get document with all pages
     */
    suspend fun getDocumentWithPages(documentId: String): Result<Document> {
        return try {
            val response =
                client.get("/api/documents/$documentId") {
                    parameter("include_pages", true)
                }

            if (response.status.isSuccess()) {
                val apiResponse = json.decodeFromString<ApiResponse<Document>>(response.bodyAsText())
                if (apiResponse.success && apiResponse.data != null) {
                    Result.success(apiResponse.data)
                } else {
                    Result.failure(Exception(apiResponse.error ?: "Failed to fetch document"))
                }
            } else {
                Result.failure(Exception("Failed to fetch document: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete document and all its pages
     */
    suspend fun deleteDocument(documentId: String): Result<Unit> {
        return try {
            val response = client.delete("/api/documents/$documentId")

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete document: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Page Management ==========

    /**
     * Add a new page to an existing document
     *
     * Backend will:
     * - Upload image to Google Cloud Storage
     * - Generate thumbnail
     * - Update document page count
     * - Return page with GCS URLs
     *
     * @param documentId Document ID to add page to
     * @param imageData Raw image bytes
     * @param pageNumber Page number for this page
     * @param width Optional image width
     * @param height Optional image height
     * @return Result with created page containing remote URLs
     */
    suspend fun addPage(
        documentId: String,
        imageData: ByteArray,
        pageNumber: Int,
        width: Int = 0,
        height: Int = 0,
    ): Result<DocumentPage> {
        return try {
            // Build serializable request
            val request =
                AddPageRequest(
                    image_data = encodeToBase64(imageData),
                    page_number = pageNumber,
                    width = width,
                    height = height,
                )

            val response =
                client.post("/api/documents/$documentId/pages") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.isSuccess()) {
                // Backend wraps response in {"success": true, "data": {...}}
                val apiResponse = json.decodeFromString<ApiResponse<DocumentPage>>(response.bodyAsText())
                if (apiResponse.success && apiResponse.data != null) {
                    Result.success(apiResponse.data)
                } else {
                    Result.failure(Exception(apiResponse.error ?: "Failed to add page"))
                }
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to add page: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a page from a document
     */
    suspend fun deletePage(
        documentId: String,
        pageId: String,
    ): Result<Unit> {
        return try {
            val response = client.delete("/api/documents/$documentId/pages/$pageId")

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete page: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reorder pages in a document (the backend replies with success only)
     */
    suspend fun reorderPages(
        documentId: String,
        pageIds: List<String>,
    ): Result<Unit> {
        return try {
            val response =
                client.put("/api/documents/$documentId/pages/reorder") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("page_order" to pageIds))
                }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to reorder pages: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Document Processing ==========

    /**
     * Search documents by query
     */
    suspend fun searchDocuments(
        query: String,
        type: DocumentType? = null,
    ): Result<List<Document>> {
        return try {
            val response =
                client.get("/api/documents/search") {
                    parameter("q", query)
                    type?.let { parameter("type", it.name) }
                }

            if (response.status.isSuccess()) {
                val apiResponse = json.decodeFromString<ApiResponse<List<Document>>>(response.bodyAsText())
                if (apiResponse.success) {
                    Result.success(apiResponse.data.orEmpty())
                } else {
                    Result.failure(Exception(apiResponse.error ?: "Failed to search documents"))
                }
            } else {
                Result.failure(Exception("Failed to search documents: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Page upload data
 *
 * Contains image data and metadata for uploading a page to the backend.
 * Used when creating a new document with multiple pages.
 *
 * @param imageData Raw image bytes
 * @param pageNumber Page number (1-indexed)
 * @param width Image width in pixels
 * @param height Image height in pixels
 */
data class PageUploadData(
    val imageData: ByteArray,
    val pageNumber: Int,
    val width: Int,
    val height: Int,
)

// ========== API Request/Response DTOs ==========

/**
 * Backend API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Page data for API requests (serializable)
 */
@Serializable
data class PageData(
    val image_data: String,
    val page_number: Int,
    val width: Int,
    val height: Int,
)

/**
 * Document scan request body
 */
@Serializable
data class DocumentScanRequest(
    val title: String,
    val type: String,
    val course_id: String? = null,
    val pages: List<PageData>,
)

/**
 * Add page request body
 */
@Serializable
data class AddPageRequest(
    val image_data: String,
    val page_number: Int,
    val width: Int,
    val height: Int,
)
