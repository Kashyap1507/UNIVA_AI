package com.kashyap.wizlyai.util

/**
 * iOS implementation of SyncLogger
 * Uses print() for logging to console
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
     * @param tag Log tag (usually class name)
     * @param operation Description of operation starting
     */
    actual fun syncStart(
        tag: String,
        operation: String,
    ) {
        if (!isDebugEnabled) return

        println("[$tag] $SEPARATOR")
        println("[$tag] 🔄 $operation")
        println("[$tag] $SEPARATOR")
    }

    /**
     * Log sync operation success
     *
     * @param tag Log tag
     * @param message Success message
     */
    actual fun syncSuccess(
        tag: String,
        message: String,
    ) {
        if (!isDebugEnabled) return

        println("[$tag] ✅ $message")
    }

    /**
     * Log sync operation failure with error details
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
        println("[$tag] ❌ $message")
        error?.let {
            println("[$tag] Error: ${it.message}")
            println("[$tag] ${it.stackTraceToString()}")
        }
    }

    /**
     * Log sync operation end with visual separator
     *
     * @param tag Log tag
     */
    actual fun syncEnd(tag: String) {
        if (!isDebugEnabled) return

        println("[$tag] $SEPARATOR")
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

        println("[$tag]    ℹ️  $message")
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
        println("[$tag] ⚠️  $message")
    }

    /**
     * Log verbose details (indented for readability)
     *
     * @param tag Log tag
     * @param message Detail message
     */
    actual fun detail(
        tag: String,
        message: String,
    ) {
        if (!isVerboseEnabled) return

        println("[$tag]    $message")
    }

    /**
     * Log sync statistics summary
     *
     * @param tag Log tag
     * @param stats Map of statistic key-value pairs
     */
    actual fun stats(
        tag: String,
        stats: Map<String, Any>,
    ) {
        if (!isDebugEnabled) return

        println("[$tag] ")
        println("[$tag] 📊 Sync Statistics:")
        stats.forEach { (key, value) ->
            println("[$tag]    - $key: $value")
        }
    }

    /**
     * Log sync progress for long-running operations
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

        println("[$tag]    🔄 $description $current/$total...")
    }

    /**
     * Log skipped operation (for smart sync)
     *
     * @param tag Log tag
     * @param reason Reason for skipping
     */
    actual fun skipped(
        tag: String,
        reason: String,
    ) {
        if (!isVerboseEnabled) return

        println("[$tag]    ⏭️  Skipped: $reason")
    }

    /**
     * Log cache invalidation
     *
     * @param tag Log tag
     * @param cacheKey Cache key that was invalidated
     */
    actual fun cacheInvalidated(
        tag: String,
        cacheKey: String,
    ) {
        if (!isDebugEnabled) return

        println("[$tag] 🧹 Cache invalidated: $cacheKey")
    }

    /**
     * Log database operation
     *
     * @param tag Log tag
     * @param operation Database operation performed
     */
    actual fun database(
        tag: String,
        operation: String,
    ) {
        if (!isVerboseEnabled) return

        println("[$tag] 💾 $operation")
    }

    /**
     * Log API call
     *
     * @param tag Log tag
     * @param endpoint API endpoint being called
     */
    actual fun apiCall(
        tag: String,
        endpoint: String,
    ) {
        if (!isDebugEnabled) return

        println("[$tag] 🌐 $endpoint")
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

        println("[$tag] $emoji $message")
    }
}
