package com.kashyap.wizlyai.util

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import okio.Path.Companion.toPath
import java.io.File

/**
 * Image Cache Manager for efficient thumbnail and document image loading
 *
 * Features:
 * - Local file:// URI loading with priority
 * - Remote URL loading with automatic disk caching
 * - Memory cache for instant display
 * - LRU eviction policy
 * - Automatic fallback from local to remote
 *
 * Usage:
 * ```kotlin
 * val imageLoader = ImageCacheManager.getImageLoader(context)
 *
 * AsyncImage(
 *     model = ImageRequest.Builder(context)
 *         .data(ImageCacheManager.getImageUri(localPath, remoteUrl))
 *         .build(),
 *     imageLoader = imageLoader,
 *     ...
 * )
 * ```
 */
object ImageCacheManager {
    private const val TAG = "ImageCacheManager"
    private const val CACHE_DIR_NAME = "wizly_image_cache"
    private const val MAX_MEMORY_CACHE_SIZE_PERCENT = 0.25 // 25% of available memory
    private const val MAX_DISK_CACHE_SIZE_MB = 250L // 250 MB

    private var imageLoader: ImageLoader? = null

    /**
     * Get or create singleton ImageLoader with optimized caching
     *
     * @param context Application context
     * @return Configured ImageLoader instance
     */
    fun getImageLoader(context: Context): ImageLoader {
        if (imageLoader == null) {
            synchronized(this) {
                if (imageLoader == null) {
                    imageLoader = createImageLoader(context.applicationContext)
                    Log.d(TAG, "✅ ImageLoader created with memory and disk cache")
                }
            }
        }
        return imageLoader!!
    }

    /**
     * Create configured ImageLoader with memory and disk caching
     */
    private fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MAX_MEMORY_CACHE_SIZE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(CACHE_DIR_NAME).absolutePath.toPath())
                    .maxSizeBytes(MAX_DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .build()
    }

    /**
     * Get image URI with local-first strategy
     *
     * Priority:
     * 1. Local file path (if exists and valid)
     * 2. Remote URL (will be cached to disk automatically)
     *
     * @param localPath Local file path (e.g., "/data/data/.../files/documents/...")
     * @param remoteUrl Remote URL (e.g., "https://storage.googleapis.com/...")
     * @return URI to load (file:// or https://)
     */
    fun getImageUri(
        localPath: String?,
        remoteUrl: String?,
    ): String? {
        // Priority 1: Local file
        if (!localPath.isNullOrEmpty()) {
            val file = File(localPath)
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "🎯 Using local file: ${file.name}")
                return "file://${file.absolutePath}"
            } else {
                Log.d(TAG, "⚠️ Local file not found or empty: ${file.absolutePath}")
            }
        }

        // Priority 2: Remote URL
        if (!remoteUrl.isNullOrEmpty() && remoteUrl.startsWith("http")) {
            Log.d(TAG, "🌐 Using remote URL: $remoteUrl")
            return remoteUrl
        }

        Log.w(TAG, "❌ No valid image source found")
        return null
    }

    /**
     * Check if local file exists and is valid
     *
     * @param localPath Local file path
     * @return true if file exists and has valid image data
     */
    fun isLocalImageValid(localPath: String?): Boolean {
        if (localPath.isNullOrEmpty()) return false

        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return false
        }

        // Quick validation: Try to decode image dimensions
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            Log.w(TAG, "Invalid image file: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * Clear all caches (memory + disk)
     *
     * @param context Application context
     */
    fun clearAllCaches(context: Context) {
        imageLoader?.let { loader ->
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            Log.d(TAG, "🧹 All image caches cleared")
        }
    }

    /**
     * Clear memory cache only (keeps disk cache)
     *
     * @param context Application context
     */
    fun clearMemoryCache(context: Context) {
        imageLoader?.memoryCache?.clear()
        Log.d(TAG, "🧹 Memory cache cleared")
    }

    /**
     * Get cache statistics
     *
     * @param context Application context
     * @return Map with cache statistics
     */
    fun getCacheStats(context: Context): Map<String, Long> {
        val stats = mutableMapOf<String, Long>()

        imageLoader?.let { loader ->
            loader.memoryCache?.let { cache ->
                stats["memorySize"] = cache.size.toLong()
                stats["memoryMaxSize"] = cache.maxSize.toLong()
            }

            loader.diskCache?.let { cache ->
                stats["diskSize"] = cache.size
                stats["diskMaxSize"] = cache.maxSize
            }
        }

        return stats
    }

    /**
     * Create ImageRequest for document thumbnail
     *
     * @param context Context
     * @param localPath Local file path
     * @param remoteUrl Remote URL
     * @return Configured ImageRequest
     */
    fun createThumbnailRequest(
        context: Context,
        localPath: String?,
        remoteUrl: String?,
    ): ImageRequest {
        val imageUri = getImageUri(localPath, remoteUrl)

        return ImageRequest.Builder(context)
            .data(imageUri)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    /**
     * Create ImageRequest for full document page
     *
     * @param context Context
     * @param localPath Local file path
     * @param remoteUrl Remote URL
     * @return Configured ImageRequest
     */
    fun createPageImageRequest(
        context: Context,
        localPath: String?,
        remoteUrl: String?,
    ): ImageRequest {
        val imageUri = getImageUri(localPath, remoteUrl)

        return ImageRequest.Builder(context)
            .data(imageUri)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
