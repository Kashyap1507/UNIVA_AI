package com.kashyap.wizlyai.ui.screens.documents

import android.content.Context
import android.util.Log
import com.kashyap.wizlyai.cache.SimpleCache
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.repository.DocumentWithPages
import com.kashyap.wizlyai.data.repository.LocalDocumentRepository
import kotlin.time.Duration.Companion.minutes

/**
 * Android implementation of LocalDocumentDetailProvider
 *
 * Loads document details from local Room database with SimpleCache integration
 *
 * Features:
 * - Instant loading from local cache
 * - SimpleCache integration for in-memory caching
 * - Automatic file:// URI for local images
 * - No server requests for fresh local data
 */
actual class LocalDocumentDetailProvider(context: Context) {
    private val localRepository = LocalDocumentRepository(context)
    private val cache = SimpleCache.instance

    companion object {
        private const val TAG = "LocalDocDetailProvider"
        private const val CACHE_PREFIX = "document_detail_"
        private val CACHE_TTL = 5.minutes // Consider data stale after 5 minutes
    }

    /**
     * Get document with pages from local storage
     *
     * Priority:
     * 1. In-memory cache (instant)
     * 2. Local Room database (very fast)
     * 3. API (only if local missing - not implemented here)
     *
     * @param documentId Document ID
     * @return Result with Document and List of Pages
     */
    actual suspend fun getDocumentWithPages(documentId: String): Result<DocumentWithPages> {
        Log.d(TAG, "📖 Loading document: $documentId")

        return try {
            // Try to get from cache first (instant)
            val cachedData =
                cache.get<DocumentWithPages>(
                    key = "$CACHE_PREFIX$documentId",
                    ttl = CACHE_TTL,
                ) {
                    // Cache miss - fetch from local database
                    Log.d(TAG, "🔄 Cache miss - loading from local database")
                    val result = localRepository.getLocalDocumentWithPages(documentId)

                    if (result.isSuccess) {
                        Log.d(TAG, "✅ Loaded from local database: ${result.getOrNull()?.document?.title}")
                        result.getOrThrow()
                    } else {
                        Log.e(TAG, "❌ Failed to load from database: ${result.exceptionOrNull()?.message}")
                        throw result.exceptionOrNull() ?: Exception("Document not found")
                    }
                }

            Result.success(cachedData)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading document: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get document metadata only (without pages)
     *
     * Faster than getDocumentWithPages when pages aren't needed.
     *
     * @param documentId Document ID
     * @return Result with Document
     */
    actual suspend fun getDocument(documentId: String): Result<Document> {
        return try {
            val result = getDocumentWithPages(documentId)
            if (result.isSuccess) {
                Result.success(result.getOrThrow().document)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Document not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Invalidate cache for specific document
     *
     * Call this after document updates to force refresh.
     *
     * @param documentId Document ID to invalidate
     */
    actual fun invalidateDocument(documentId: String) {
        cache.invalidate("$CACHE_PREFIX$documentId")
        Log.d(TAG, "🗑️ Invalidated cache for document: $documentId")
    }

    /**
     * Invalidate all document detail caches
     *
     * Call this after bulk operations or sync.
     */
    actual fun invalidateAll() {
        cache.invalidatePattern(CACHE_PREFIX)
        Log.d(TAG, "🧹 Invalidated all document detail caches")
    }

    /**
     * Prefetch document for faster access later
     *
     * Useful for loading documents in the background before user opens them.
     *
     * @param documentId Document ID to prefetch
     */
    actual suspend fun prefetch(documentId: String) {
        try {
            getDocumentWithPages(documentId)
            Log.d(TAG, "🎯 Prefetched document: $documentId")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to prefetch document: $documentId", e)
        }
    }

    /**
     * Prefetch multiple documents
     *
     * @param documentIds List of document IDs to prefetch
     */
    actual suspend fun prefetchMultiple(documentIds: List<String>) {
        documentIds.forEach { documentId ->
            prefetch(documentId)
        }
    }
}
