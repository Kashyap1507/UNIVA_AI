package com.kashyap.wizlyai.util

/**
 * Centralized logging utility for sync operations
 *
 * Benefits:
 * - Consistent log formatting across all sync components
 * - Eliminates ~150 lines of duplicate logging patterns
 * - Easy to change logging strategy globally (e.g., add analytics, remote logging)
 * - Standardized visual separators and emoji indicators
 * - Centralized log level control
 *
 * Platform-specific implementations:
 * - Android: Uses android.util.Log
 * - iOS: Uses print() statements
 */
expect object SyncLogger {
    /**
     * Enable/disable debug logging
     */
    fun setDebugEnabled(enabled: Boolean)

    /**
     * Enable/disable verbose logging
     */
    fun setVerboseEnabled(enabled: Boolean)

    /**
     * Log sync operation start with visual separator
     *
     * @param tag Log tag (usually class name)
     * @param operation Description of operation starting
     */
    fun syncStart(
        tag: String,
        operation: String,
    )

    /**
     * Log sync operation success
     *
     * @param tag Log tag
     * @param message Success message
     */
    fun syncSuccess(
        tag: String,
        message: String,
    )

    /**
     * Log sync operation failure with error details
     *
     * @param tag Log tag
     * @param message Error message
     * @param error Optional throwable for stack trace
     */
    fun syncFailure(
        tag: String,
        message: String,
        error: Throwable? = null,
    )

    /**
     * Log sync operation end with visual separator
     *
     * @param tag Log tag
     */
    fun syncEnd(tag: String)

    /**
     * Log info message with emoji indicator
     *
     * @param tag Log tag
     * @param message Info message
     */
    fun info(
        tag: String,
        message: String,
    )

    /**
     * Log warning message with emoji indicator
     *
     * @param tag Log tag
     * @param message Warning message
     */
    fun warning(
        tag: String,
        message: String,
    )

    /**
     * Log verbose details (indented for readability)
     *
     * @param tag Log tag
     * @param message Detail message
     */
    fun detail(
        tag: String,
        message: String,
    )

    /**
     * Log sync statistics summary
     *
     * @param tag Log tag
     * @param stats Map of statistic key-value pairs
     */
    fun stats(
        tag: String,
        stats: Map<String, Any>,
    )

    /**
     * Log sync progress for long-running operations
     *
     * @param tag Log tag
     * @param current Current progress
     * @param total Total items
     * @param description Optional description
     */
    fun progress(
        tag: String,
        current: Int,
        total: Int,
        description: String = "Processing",
    )

    /**
     * Log skipped operation (for smart sync)
     *
     * @param tag Log tag
     * @param reason Reason for skipping
     */
    fun skipped(
        tag: String,
        reason: String,
    )

    /**
     * Log cache invalidation
     *
     * @param tag Log tag
     * @param cacheKey Cache key that was invalidated
     */
    fun cacheInvalidated(
        tag: String,
        cacheKey: String,
    )

    /**
     * Log database operation
     *
     * @param tag Log tag
     * @param operation Database operation performed
     */
    fun database(
        tag: String,
        operation: String,
    )

    /**
     * Log API call
     *
     * @param tag Log tag
     * @param endpoint API endpoint being called
     */
    fun apiCall(
        tag: String,
        endpoint: String,
    )

    /**
     * Log with custom emoji indicator
     *
     * @param tag Log tag
     * @param emoji Custom emoji
     * @param message Log message
     */
    fun custom(
        tag: String,
        emoji: String,
        message: String,
    )
}
