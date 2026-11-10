package com.kashyap.wizlyai.ui.screens.documents

import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.ios.IOSCallbackBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Kotlin wrapper for Swift IOSDocumentProvider
 * Bridges between Kotlin and Swift CoreData implementation
 */
class IOSDocumentProviderWrapper : DocumentProvider {
    override suspend fun getDocuments(userId: String): List<Document> {
        println("📖 iOS DocumentProvider: Getting documents for user $userId")

        val callbacks = IOSCallbackBridge.getDocumentQueryCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS DocumentProvider: Callbacks not set, returning empty list")
            return emptyList()
        }

        return try {
            // Add 10-second timeout to prevent infinite hanging
            withTimeout(10000L) {
                suspendCancellableCoroutine { continuation ->
                    println("📞 iOS DocumentProvider: Calling Swift getDocuments via callback...")
                    callbacks.getDocuments(
                        userId,
                        { documents ->
                            println("✅ iOS DocumentProvider: Received ${documents.size} documents from Swift")
                            continuation.resume(documents)
                        },
                        { error ->
                            println("❌ iOS DocumentProvider: Error from Swift - ${error.message}")
                            continuation.resume(emptyList())
                        },
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ iOS DocumentProvider: Timeout or error - ${e.message}")
            emptyList()
        }
    }

    override suspend fun getDocumentWithPages(documentId: String): Document {
        println("📖 iOS DocumentProvider: Getting document with pages: $documentId")

        val callbacks = IOSCallbackBridge.getDocumentQueryCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS DocumentProvider: Callbacks not set, returning empty document")
            return Document(
                id = documentId,
                title = "Error: Callbacks Not Set",
                type = com.kashyap.wizlyai.data.models.DocumentType.HOMEWORK,
                pages = emptyList(),
                createdAt = "",
                updatedAt = "",
            )
        }

        return suspendCancellableCoroutine { continuation ->
            println("📞 iOS DocumentProvider: Calling Swift getDocumentWithPages via callback...")
            callbacks.getDocumentWithPages(
                documentId,
                { docWithPages ->
                    println("✅ iOS DocumentProvider: Received document with pages from Swift")
                    println("   Document title: ${docWithPages.document.title}")
                    println("   Pages in document.pages: ${docWithPages.document.pages?.size ?: 0}")
                    println("   Pages in separate array: ${docWithPages.pages.size}")

                    // Merge pages from docWithPages.pages into document
                    // (Swift sets document.pages=nil and keeps pages in separate array)
                    val documentWithPages = docWithPages.document.copy(pages = docWithPages.pages)
                    println("✅ Merged pages into document: ${documentWithPages.pages?.size ?: 0} pages")

                    continuation.resume(documentWithPages)
                },
                { error ->
                    println("❌ iOS DocumentProvider: Error from Swift - ${error.message}")
                    println("   Returning empty document instead of throwing exception")
                    // Return empty document instead of throwing to prevent white screen crash
                    continuation.resume(
                        Document(
                            id = documentId,
                            title = "Error Loading Document",
                            type = com.kashyap.wizlyai.data.models.DocumentType.HOMEWORK,
                            pages = emptyList(),
                            createdAt = "",
                            updatedAt = "",
                        ),
                    )
                },
            )
        }
    }

    override suspend fun searchDocuments(
        userId: String,
        query: String,
    ): List<Document> {
        println("🔍 iOS DocumentProvider: Searching documents for user $userId with query: $query")

        val callbacks = IOSCallbackBridge.getDocumentQueryCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS DocumentProvider: Callbacks not set, returning empty list")
            return emptyList()
        }

        return suspendCancellableCoroutine { continuation ->
            println("📞 iOS DocumentProvider: Calling Swift searchDocuments via callback...")
            callbacks.searchDocuments(
                userId,
                query,
                50, // Default limit
                { documents ->
                    println("✅ iOS DocumentProvider: Found ${documents.size} documents matching '$query'")
                    continuation.resume(documents)
                },
                { error ->
                    println("❌ iOS DocumentProvider: Error from Swift - ${error.message}")
                    continuation.resume(emptyList())
                },
            )
        }
    }

    override suspend fun deleteDocument(documentId: String): Result<Unit> {
        println("🗑️ iOS DocumentProvider: Deleting document $documentId")
        return try {
            // TODO: Wire to Swift deletion callback when implemented
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPendingSyncCount(): Int {
        println("📊 iOS DocumentProvider: Getting pending sync count")

        val callbacks = IOSCallbackBridge.getDocumentQueryCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS DocumentProvider: Callbacks not set, returning 0")
            return 0
        }

        // Get all pending documents and count them
        // TODO: Add dedicated getPendingCount callback if needed
        return 0
    }

    override suspend fun getStorageUsed(): Long {
        println("💾 iOS DocumentProvider: Getting storage used")

        val storageCallbacks = IOSCallbackBridge.getDocumentStorageCallbacks()
        if (storageCallbacks == null) {
            println("⚠️ iOS DocumentProvider: Storage callbacks not set, returning 0")
            return 0L
        }

        return suspendCancellableCoroutine { continuation ->
            storageCallbacks.getStorageUsed { size ->
                println("✅ iOS DocumentProvider: Storage used: $size bytes")
                continuation.resume(size)
            }
        }
    }
}

/**
 * Factory function to create iOS DocumentProvider
 */
fun createIOSDocumentProvider(): DocumentProvider {
    return IOSDocumentProviderWrapper()
}
