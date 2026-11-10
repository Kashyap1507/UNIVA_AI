package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.ios.IOSCallbackBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Kotlin wrapper for Swift IOSLocalDocumentStorage
 * Bridges between Kotlin and Swift CoreData implementation via callbacks
 */
class IOSLocalDocumentStorageWrapper : LocalDocumentStorage {
    override suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> =
        suspendCancellableCoroutine { continuation ->
            println("🔵 iOS: Saving document locally")
            println("   User ID: $userId")
            println("   Title: $title")
            println("   Type: ${type.name}")
            println("   Images: ${imagePaths.size}")

            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift saveDocument via callback...")
                callbacks.saveDocument(
                    userId,
                    title,
                    type,
                    imagePaths,
                    { documentId: String -> continuation.resume(Result.success(documentId)) },
                    { error: Throwable -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(Result.failure(Exception("Document storage callbacks not set")))
            }
        }

    override suspend fun saveDocumentWithId(
        documentId: String,
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        println("🔵 iOS: Saving document with preserved ID")
        println("   Document ID: $documentId (from API)")
        println("   User ID: $userId")
        println("   Title: $title")
        println("   Type: ${type.name}")
        println("   Images: ${imagePaths.size}")

        // Server documents are loaded from the API on iOS; nothing to store locally
        println("ℹ️ iOS: saveDocumentWithId keeps the server copy only")
        return Result.success(documentId)
    }

    override suspend fun addPageLocally(
        documentId: String,
        imagePath: String,
    ): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            println("🔵 iOS: Adding page to document $documentId")

            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift addPage via callback...")
                callbacks.addPage(
                    documentId,
                    imagePath,
                    { -> continuation.resume(Result.success(Unit)) },
                    { error: Throwable -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(Result.failure(Exception("Document storage callbacks not set")))
            }
        }

    override suspend fun deletePageLocally(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
    ): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            println("🔵 iOS: Deleting page $pageId from document $documentId")

            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift deletePage via callback...")
                callbacks.deletePage(
                    documentId,
                    pageId,
                    userId,
                    pageNumber,
                    { -> continuation.resume(Result.success(Unit)) },
                    { error: Throwable -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(Result.failure(Exception("Document storage callbacks not set")))
            }
        }

    override suspend fun deleteDocumentLocally(
        documentId: String,
        userId: String,
    ): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                callbacks.deleteDocument(
                    documentId,
                    { -> continuation.resume(Result.success(Unit)) },
                    { error: Throwable -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(Result.failure(Exception("Document storage callbacks not set")))
            }
        }

    override suspend fun getLocalDocument(documentId: String): Result<Document> =
        suspendCancellableCoroutine { continuation ->
            println("🔵 iOS: Getting local document $documentId")

            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift getDocument via callback...")
                callbacks.getDocument(
                    documentId,
                    { document: com.kashyap.wizlyai.data.models.Document -> continuation.resume(Result.success(document)) },
                    { error: Throwable -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(Result.failure(Exception("Document storage callbacks not set")))
            }
        }

    override suspend fun markForSync(documentId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            println("🔵 iOS: Marking document $documentId for sync")

            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift markForSync via callback...")
                callbacks.markForSync(
                    documentId,
                    { -> continuation.resume(Result.success(Unit)) },
                    { error: Throwable -> continuation.resume(Result.failure(error)) },
                )
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(Result.failure(Exception("Document storage callbacks not set")))
            }
        }

    override suspend fun getStorageUsed(): Long =
        suspendCancellableCoroutine { continuation ->
            println("🔵 iOS: Getting storage used")

            val callbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
            if (callbacks != null) {
                println("📞 iOS: Calling Swift getStorageUsed via callback...")
                callbacks.getStorageUsed { storageUsed ->
                    continuation.resume(storageUsed)
                }
            } else {
                println("⚠️ iOS: Document storage callbacks not set!")
                continuation.resume(0L)
            }
        }
}


/**
 * Factory function to create iOS LocalDocumentStorage
 */
fun createIOSLocalDocumentStorage(): LocalDocumentStorage {
    return IOSLocalDocumentStorageWrapper()
}
