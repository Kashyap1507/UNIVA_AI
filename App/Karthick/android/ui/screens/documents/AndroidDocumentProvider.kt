package com.kashyap.wizlyai.ui.screens.documents

import android.content.Context
import android.util.Log
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.repository.LocalDocumentRepository

/**
 * Android implementation of DocumentProvider using LocalDocumentRepository
 * This provides a simple list-based interface for documents
 * For pagination UI, use LocalDocumentsViewModel directly with Paging 3
 */
class AndroidDocumentProvider(context: Context) : DocumentProvider {
    private val localRepository = LocalDocumentRepository(context)

    override suspend fun getDocuments(userId: String): List<Document> {
        // For simple non-paginated views, return first batch
        // Note: For large datasets, prefer using LocalDocumentsViewModel with Paging 3
        Log.d("AndroidDocumentProvider", "═══════════════════════════════════════════════════════")
        Log.d("AndroidDocumentProvider", "📖 getDocuments called")
        Log.d("AndroidDocumentProvider", "   User ID: '$userId'")
        Log.d("AndroidDocumentProvider", "   Query: '' (empty string = all documents)")
        val result = localRepository.searchLocalDocuments(userId, "", limit = 50)

        return result.fold(
            onSuccess = { documents ->
                Log.d("AndroidDocumentProvider", "✅ Query successful: Found ${documents.size} documents")
                if (documents.isEmpty()) {
                    Log.w("AndroidDocumentProvider", "⚠️ No documents found for this user")
                    Log.w("AndroidDocumentProvider", "   This could mean:")
                    Log.w("AndroidDocumentProvider", "   1. No documents saved for this userId")
                    Log.w("AndroidDocumentProvider", "   2. Documents were saved with different userId")
                    Log.w("AndroidDocumentProvider", "   3. Documents marked as deleted (is_deleted = 1)")
                } else {
                    documents.forEach { doc ->
                        Log.d("AndroidDocumentProvider", "   📄 ${doc.title}")
                        Log.d("AndroidDocumentProvider", "       ID: ${doc.id}")
                        Log.d("AndroidDocumentProvider", "       Type: ${doc.type}")
                        Log.d("AndroidDocumentProvider", "       Created: ${doc.createdAt}")
                    }
                }
                Log.d("AndroidDocumentProvider", "═══════════════════════════════════════════════════════")
                documents
            },
            onFailure = { error ->
                Log.e("AndroidDocumentProvider", "❌ Query failed")
                Log.e("AndroidDocumentProvider", "   Error: ${error.message}")
                Log.e("AndroidDocumentProvider", "   Type: ${error::class.simpleName}")
                error.printStackTrace()
                Log.d("AndroidDocumentProvider", "═══════════════════════════════════════════════════════")
                emptyList()
            },
        )
    }

    override suspend fun getDocumentWithPages(documentId: String): Document {
        Log.d("AndroidDocumentProvider", "═══════════════════════════════════════════════════════")
        Log.d("AndroidDocumentProvider", "📄 getDocumentWithPages called")
        Log.d("AndroidDocumentProvider", "   Document ID: $documentId")

        val result = localRepository.getLocalDocumentWithPages(documentId)
        return result.getOrElse { error ->
            Log.e("AndroidDocumentProvider", "❌ Failed to get document from local repository")
            Log.e("AndroidDocumentProvider", "   Error: ${error.message}")
            Log.e("AndroidDocumentProvider", "   Error type: ${error::class.simpleName}")
            error.printStackTrace()
            Log.d("AndroidDocumentProvider", "═══════════════════════════════════════════════════════")
            throw error
        }.let { docWithPages ->
            Log.d("AndroidDocumentProvider", "✅ Document loaded successfully from Room database")
            Log.d("AndroidDocumentProvider", "   Title: ${docWithPages.document.title}")
            Log.d("AndroidDocumentProvider", "   Pages: ${docWithPages.pages.size}")
            Log.d("AndroidDocumentProvider", "   User ID: ${docWithPages.document.userId}")
            Log.d("AndroidDocumentProvider", "   Document status: ${docWithPages.document.status}")
            Log.d("AndroidDocumentProvider", "═══════════════════════════════════════════════════════")
            // Return document with pages embedded
            docWithPages.document.copy(pages = docWithPages.pages)
        }
    }

    override suspend fun searchDocuments(
        userId: String,
        query: String,
    ): List<Document> {
        return localRepository.searchLocalDocuments(userId, query).getOrNull() ?: emptyList()
    }

    override suspend fun deleteDocument(documentId: String): Result<Unit> {
        return try {
            // Get document to retrieve userId
            val document =
                localRepository.getLocalDocumentWithPages(documentId).fold(
                    onSuccess = { it.document },
                    onFailure = { return Result.failure(it) },
                )

            // Perform full deletion (local + server)
            localRepository.hardDeleteDocument(
                documentId = documentId,
                userId = document.userId,
                deleteFromServer = true,
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPendingSyncCount(): Int {
        return localRepository.getPendingSyncDocuments().getOrNull()?.size ?: 0
    }

    override suspend fun getStorageUsed(): Long {
        return localRepository.getTotalStorageUsed()
    }
}
