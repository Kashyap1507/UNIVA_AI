package com.kashyap.wizlyai.util

import android.util.Log

/**
 * Android implementation of SyncLogger
 * Uses android.util.Log for logging
 *
 * Replaces duplicate logging patterns in:
 * - BootstrapSync.android.kt
 * - BootstrapSyncManager.kt
 * - LocalDataSyncManager.kt
 * - LocalChildRepository.kt
 * - LocalParentRepository.kt
 * - LocalDocumentRepository.kt
 */
actual object SyncLogger {
    private const val SEPARATOR = "═══════════════════════════════════════════════════════"

    // Log levels - can be configured globally
    private var isDebugEnabled = true
    private var isVerboseEnabled = true

    /**
     * Enable/disable debug logging
     */
    actual fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    /**
     * Enable/disable verbose logging
     */
    actual fun setVerboseEnabled(enabled: Boolean) {
        isVerboseEnabled = enabled
    }

    /**
     * Log sync operation start with visual separator
     *
     * Example output:
     * ═══════════════════════════════════════════════════════
     * 🔄 Syncing bootstrap data to Room database
     * ═══════════════════════════════════════════════════════
     *
     * @param tag Log tag (usually class name)
     * @param operation Description of operation starting
     */
    actual fun syncStart(
        tag: String,
        operation: String,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, SEPARATOR)
        Log.d(tag, "🔄 $operation")
        Log.d(tag, SEPARATOR)
    }

    /**
     * Log sync operation success
     *
     * Example: ✅ Bootstrap data synced: 15 items
     *
     * @param tag Log tag
     * @param message Success message
     */
    actual fun syncSuccess(
        tag: String,
        message: String,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, "✅ $message")
    }

    /**
     * Log sync operation failure with error details
     *
     * Example: ❌ Bootstrap sync failed: Database locked
     *
     * @param tag Log tag
     * @param message Error message
     * @param error Optional throwable for stack trace
     */
    actual fun syncFailure(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        Log.e(tag, "❌ $message", error)
    }

    /**
     * Log sync operation end with visual separator
     *
     * @param tag Log tag
     */
    actual fun syncEnd(tag: String) {
        if (!isDebugEnabled) return

        Log.d(tag, SEPARATOR)
    }

    /**
     * Log info message with emoji indicator
     *
     * @param tag Log tag
     * @param message Info message
     */
    actual fun info(
        tag: String,
        message: String,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, "   ℹ️  $message")
    }

    /**
     * Log warning message with emoji indicator
     *
     * @param tag Log tag
     * @param message Warning message
     */
    actual fun warning(
        tag: String,
        message: String,
    ) {
        Log.w(tag, "⚠️  $message")
    }

    /**
     * Log verbose details (indented for readability)
     *
     * Example:
     *    - Course: COMP-8117 (Advanced Software Engineering)
     *    - Course: COMP-8547 (Machine Learning)
     *
     * @param tag Log tag
     * @param message Detail message
     */
    actual fun detail(
        tag: String,
        message: String,
    ) {
        if (!isVerboseEnabled) return

        Log.d(tag, "   $message")
    }

    /**
     * Log sync statistics summary
     *
     * Example:
     * 📊 Sync Statistics:
     *    - Items synced: 15
     *    - Duration: 234ms
     *    - Success rate: 100%
     *
     * @param tag Log tag
     * @param stats Map of statistic key-value pairs
     */
    actual fun stats(
        tag: String,
        stats: Map<String, Any>,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, "")
        Log.d(tag, "📊 Sync Statistics:")
        stats.forEach { (key, value) ->
            Log.d(tag, "   - $key: $value")
        }
    }

    /**
     * Log sync progress for long-running operations
     *
     * Example: 🔄 Processing entity 5/15...
     *
     * @param tag Log tag
     * @param current Current progress
     * @param total Total items
     * @param description Optional description
     */
    actual fun progress(
        tag: String,
        current: Int,
        total: Int,
        description: String,
    ) {
        if (!isVerboseEnabled) return

        Log.d(tag, "   🔄 $description $current/$total...")
    }

    /**
     * Log skipped operation (for smart sync)
     *
     * Example: ⏭️  Skipped: Local data is newer
     *
     * @param tag Log tag
     * @param reason Reason for skipping
     */
    actual fun skipped(
        tag: String,
        reason: String,
    ) {
        if (!isVerboseEnabled) return

        Log.d(tag, "   ⏭️  Skipped: $reason")
    }

    /**
     * Log cache invalidation
     *
     * Example: 🧹 Cache invalidated: documents
     *
     * @param tag Log tag
     * @param cacheKey Cache key that was invalidated
     */
    actual fun cacheInvalidated(
        tag: String,
        cacheKey: String,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, "🧹 Cache invalidated: $cacheKey")
    }

    /**
     * Log database operation
     *
     * Example: 💾 Inserted 15 entities to Room
     *
     * @param tag Log tag
     * @param operation Database operation performed
     */
    actual fun database(
        tag: String,
        operation: String,
    ) {
        if (!isVerboseEnabled) return

        Log.d(tag, "💾 $operation")
    }

    /**
     * Log API call
     *
     * Example: 🌐 Fetching documents from API...
     *
     * @param tag Log tag
     * @param endpoint API endpoint being called
     */
    actual fun apiCall(
        tag: String,
        endpoint: String,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, "🌐 $endpoint")
    }

    /**
     * Log with custom emoji indicator
     *
     * @param tag Log tag
     * @param emoji Custom emoji
     * @param message Log message
     */
    actual fun custom(
        tag: String,
        emoji: String,
        message: String,
    ) {
        if (!isDebugEnabled) return

        Log.d(tag, "$emoji $message")
    }
}
