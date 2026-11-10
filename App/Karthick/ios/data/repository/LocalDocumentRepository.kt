package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.data.models.ProcessingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSUUID
import kotlin.coroutines.resume

/**
 * iOS implementation of LocalDocumentRepository
 * Delegates to Swift IOSLocalDocumentStorage implementation
 *
 * Note: This is a simplified bridge. The actual implementation is in Swift.
 * For full functionality, use the Swift implementation directly via IOSCallbackBridge.
 */
actual class LocalDocumentRepository {
    private val _pendingSyncCount = MutableStateFlow(0)

    /**
     * Save document locally with pages
     * Automatically marks as pending sync
     *
     * @return Result with documentId on success
     */
    actual suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        return try {
            // Generate document ID
            val documentId = NSUUID().UUIDString()

            println("💾 iOS LocalDocumentRepository: Saving document")
            println("   Document ID: $documentId")
            println("   Title: $title")
            println("   Pages: ${imagePaths.size}")

            // Note: Actual storage is handled by Swift IOSLocalDocumentStorage
            // This Kotlin wrapper provides API compatibility
            // Real implementation should call Swift code via bridge

            _pendingSyncCount.value = _pendingSyncCount.value + 1
            Result.success(documentId)
        } catch (e: Exception) {
            println("❌ iOS LocalDocumentRepository: Error saving document - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all local documents for a user
     *
     * Calls Swift WizlyDatabase via IOSCallbackBridge
     */
    suspend fun getLocalDocuments(userId: String): Result<List<Document>> =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            println("📖 iOS LocalDocumentRepository: Getting documents for user $userId")

            val callbacks = com.kashyap.wizlyai.ios.IOSCallbackBridge.getDocumentQueryCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift getDocuments via callback...")
                callbacks.getDocuments(
                    userId,
                    { documents -> continuation.resume(Result.success(documents)) },
                    { error -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document query callbacks not set!")
                continuation.resume(Result.failure(Exception("Document query callbacks not set")))
            }
        }

    /**
     * Search local documents
     *
     * Calls Swift WizlyDatabase via IOSCallbackBridge
     */
    suspend fun searchLocalDocuments(
        userId: String,
        query: String,
        limit: Int = 50,
    ): Result<List<Document>> =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            println("🔍 iOS LocalDocumentRepository: Searching documents for user $userId, query: $query")

            val callbacks = com.kashyap.wizlyai.ios.IOSCallbackBridge.getDocumentQueryCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift searchDocuments via callback...")
                callbacks.searchDocuments(
                    userId,
                    query,
                    limit,
                    { documents -> continuation.resume(Result.success(documents)) },
                    { error -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document query callbacks not set!")
                continuation.resume(Result.failure(Exception("Document query callbacks not set")))
            }
        }

    /**
     * Get document count for a user
     *
     * Calls Swift WizlyDatabase via IOSCallbackBridge
     */
    suspend fun getDocumentCount(userId: String): Int =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            println("📊 iOS LocalDocumentRepository: Getting document count for user $userId")

            val callbacks = com.kashyap.wizlyai.ios.IOSCallbackBridge.getDocumentQueryCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift getDocumentCount via callback...")
                callbacks.getDocumentCount(
                    userId,
                    { count -> continuation.resume(count) },
                    { error ->
                        println("⚠️ iOS: Error getting document count: ${error.message}")
                        continuation.resume(0) // Return 0 on error
                    },
                )
            } else {
                println("⚠️ iOS: Document query callbacks not set!")
                continuation.resume(0)
            }
        }

    /**
     * Get document with pages
     *
     * Calls Swift WizlyDatabase via IOSCallbackBridge
     */
    suspend fun getLocalDocumentWithPages(documentId: String): Result<DocumentWithPages> =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            println("📖 iOS LocalDocumentRepository: Getting document with pages: $documentId")

            val callbacks = com.kashyap.wizlyai.ios.IOSCallbackBridge.getDocumentQueryCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift getDocumentWithPages via callback...")
                callbacks.getDocumentWithPages(
                    documentId,
                    { docWithPages -> continuation.resume(Result.success(docWithPages)) },
                    { error -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document query callbacks not set!")
                continuation.resume(Result.failure(Exception("Document query callbacks not set")))
            }
        }

    /**
     * Get document by ID (offline)
     */
    actual suspend fun getLocalDocument(documentId: String): Result<Document> {
        return try {
            // Note: Actual retrieval is handled by Swift IOSLocalDocumentStorage
            // This is a placeholder implementation
            println("📖 iOS LocalDocumentRepository: Getting document $documentId")

            // Return mock document for now
            // Real implementation should call Swift code via bridge
            val document =
                Document(
                    id = documentId,
                    title = "Document",
                    type = DocumentType.HOMEWORK,
                    status = ProcessingStatus.PENDING,
                    createdAt = "",
                    updatedAt = "",
                )

            Result.success(document)
        } catch (e: Exception) {
            println("❌ iOS LocalDocumentRepository: Error getting document - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Add page to existing document
     */
    actual suspend fun addPageLocally(
        documentId: String,
        imagePath: String,
    ): Result<Unit> {
        return try {
            println("➕ iOS LocalDocumentRepository: Adding page to document $documentId")
            // Note: Actual implementation in Swift IOSLocalDocumentStorage
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ iOS LocalDocumentRepository: Error adding page - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete page from document
     */
    actual suspend fun deletePageLocally(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
    ): Result<Unit> {
        return try {
            println("🗑️ iOS LocalDocumentRepository: Deleting page $pageNumber from document $documentId")
            // Note: Actual implementation in Swift IOSLocalDocumentStorage
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ iOS LocalDocumentRepository: Error deleting page - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Hard delete document and all files
     */
    actual suspend fun hardDeleteDocument(
        documentId: String,
        userId: String,
        deleteFromServer: Boolean,
    ): Result<Unit> {
        return try {
            println("🗑️ iOS LocalDocumentRepository: Hard deleting document $documentId")
            _pendingSyncCount.value = maxOf(0, _pendingSyncCount.value - 1)
            // Note: Actual implementation in Swift IOSLocalDocumentStorage
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ iOS LocalDocumentRepository: Error deleting document - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Mark document for sync
     */
    actual suspend fun markForSync(documentId: String): Result<Unit> {
        return try {
            println("🔄 iOS LocalDocumentRepository: Marking document $documentId for sync")
            _pendingSyncCount.value = _pendingSyncCount.value + 1
            // Note: Actual implementation in Swift IOSLocalDocumentStorage
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ iOS LocalDocumentRepository: Error marking for sync - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get pending sync count (observable)
     */
    actual fun getPendingSyncCountFlow(): Flow<Int> {
        return _pendingSyncCount.asStateFlow()
    }

    /**
     * Get storage usage in bytes
     */
    actual suspend fun getTotalStorageUsed(): Long {
        // Note: Actual implementation in Swift FileStorageManager
        // For now, return 0
        return 0L
    }
}
