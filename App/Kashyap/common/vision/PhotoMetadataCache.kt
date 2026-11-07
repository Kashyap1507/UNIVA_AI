package com.kashyap.wizlyai.vision

/**
 * In-memory cache for photo edge detection metadata.
 *
 * Provides LRU caching of edge detection results to avoid recomputing
 * when user re-edits or reviews photos.
 */
object PhotoMetadataCache {
    private val memoryCache = mutableMapOf<String, DocumentEdges>()
    private val accessOrder = mutableListOf<String>()
    private const val MAX_CACHE_SIZE = 20

    /**
     * Save edge detection results for a photo.
     *
     * @param photoId Unique identifier for the photo
     * @param edges Detected document edges
     */
    fun save(
        photoId: String,
        edges: DocumentEdges,
    ) {
        // LRU eviction if cache is full
        if (memoryCache.size >= MAX_CACHE_SIZE && !memoryCache.containsKey(photoId)) {
            val oldestKey = accessOrder.firstOrNull()
            if (oldestKey != null) {
                memoryCache.remove(oldestKey)
                accessOrder.remove(oldestKey)
            }
        }

        // Update cache
        memoryCache[photoId] = edges

        // Update access order (move to end)
        accessOrder.remove(photoId)
        accessOrder.add(photoId)
    }

    /**
     * Retrieve cached edge detection results.
     *
     * @param photoId Unique identifier for the photo
     * @return DocumentEdges if cached, null otherwise
     */
    fun get(photoId: String): DocumentEdges? {
        val edges = memoryCache[photoId]

        if (edges != null) {
            // Update access order (move to end for LRU)
            accessOrder.remove(photoId)
            accessOrder.add(photoId)
        }

        return edges
    }

    /**
     * Remove a photo from the cache.
     *
     * @param photoId Unique identifier for the photo
     */
    fun remove(photoId: String) {
        memoryCache.remove(photoId)
        accessOrder.remove(photoId)
    }

    /**
     * Clear all cached data.
     */
    fun clear() {
        memoryCache.clear()
        accessOrder.clear()
    }

    /**
     * Get current cache size.
     *
     * @return Number of cached entries
     */
    fun size(): Int {
        return memoryCache.size
    }
}
