package com.kashyap.wizlyai.cache

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Simple in-memory cache to prevent redundant API calls during navigation
 *
 * Usage:
 * ```
 * val cachedData = SimpleCache.instance.get<List<Course>>("courses") {
 *     // Only called if cache is empty or expired
 *     repository.getCourses()
 * }
 * ```
 */
class SimpleCache private constructor() {
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Instant,
    )

    private val cache = mutableMapOf<String, CacheEntry<*>>()
    private val defaultTTL = 5.minutes // Cache for 5 minutes

    companion object {
        val instance: SimpleCache = SimpleCache()
    }

    /**
     * Get cached data or fetch new data if cache is expired
     *
     * @param key Unique cache key
     * @param ttl Time-to-live for cache (default: 5 minutes)
     * @param fetcher Suspend function to fetch data if cache is empty/expired
     */
    suspend fun <T> get(
        key: String,
        ttl: Duration = defaultTTL,
        fetcher: suspend () -> T,
    ): T {
        val now = Clock.System.now()
        val entry = cache[key] as? CacheEntry<T>

        // Return cached data if valid
        if (entry != null && (now - entry.timestamp) < ttl) {
            println("🎯 Cache HIT: $key (age: ${now - entry.timestamp})")
            return entry.data
        }

        // Fetch new data
        println("🔄 Cache MISS: $key - fetching fresh data")
        val data = fetcher()
        cache[key] = CacheEntry(data, now)
        return data
    }

    /**
     * Invalidate specific cache key
     */
    fun invalidate(key: String) {
        cache.remove(key)
        println("🗑️ Cache invalidated: $key")
    }

    /**
     * Invalidate all cache keys matching a pattern
     */
    fun invalidatePattern(pattern: String) {
        val keysToRemove = cache.keys.filter { it.contains(pattern) }
        keysToRemove.forEach { cache.remove(it) }
        println("🗑️ Cache invalidated: ${keysToRemove.size} keys matching '$pattern'")
    }

    /**
     * Clear all cache
     */
    fun clear() {
        cache.clear()
        println("🧹 All cache cleared")
    }

    /**
     * Force update cache with new data
     */
    fun <T> set(
        key: String,
        data: T,
    ) {
        cache[key] = CacheEntry(data, Clock.System.now())
        println("💾 Cache updated: $key")
    }
}
