package com.kashyap.wizlyai.analytics

/**
 * Platform-agnostic interface for analytics tracking
 *
 * This expect/actual pattern allows shared code (in commonMain) to log analytics events
 * without tight coupling to platform-specific implementations (Firebase Analytics).
 *
 * Implementations:
 * - Android: Uses Firebase Analytics for Android
 * - iOS: Uses Firebase Analytics for iOS
 *
 * Usage:
 * ```
 * // Track screen view
 * AnalyticsTracker.logScreenView("home_screen")
 *
 * // Track event with parameters
 * AnalyticsTracker.logEvent(
 *     AnalyticsEvents.WORK_SCAN_UPLOADED,
 *     mapOf("course_id" to courseId, "kind" to "midterm")
 * )
 *
 * // Set user properties
 * AnalyticsTracker.setUserId(userId)
 * AnalyticsTracker.setUserProperty("degree_level", "masters")
 * ```
 */
expect object AnalyticsTracker {
    /**
     * Log a custom analytics event
     *
     * @param eventName The name of the event (use AnalyticsEvents constants)
     * @param params Optional parameters to attach to the event
     */
    fun logEvent(
        eventName: String,
        params: Map<String, Any> = emptyMap(),
    )

    /**
     * Log a screen view event
     *
     * This is a convenience method for tracking screen navigation.
     *
     * @param screenName The name of the screen (use AnalyticsScreens constants)
     * @param screenClass Optional class name of the screen
     */
    fun logScreenView(
        screenName: String,
        screenClass: String? = null,
    )

    /**
     * Set the user ID for analytics
     *
     * Call this after successful authentication to track user-specific analytics.
     *
     * @param userId The unique user ID
     */
    fun setUserId(userId: String?)

    /**
     * Set a user property
     *
     * User properties are attributes you define to describe segments of your user base.
     *
     * @param name The property name (e.g., "subscription_type", "course_count")
     * @param value The property value
     */
    fun setUserProperty(
        name: String,
        value: String,
    )

    /**
     * Enable or disable analytics collection
     *
     * This respects user privacy preferences. When disabled, no analytics data is sent.
     *
     * @param enabled Whether analytics collection is enabled
     */
    fun setAnalyticsEnabled(enabled: Boolean)

    /**
     * Log app open event
     *
     * Call this when the app is launched or comes to foreground.
     */
    fun logAppOpen()

    /**
     * Log a timed event (start)
     *
     * Use this with logTimedEventEnd to measure duration of operations.
     *
     * @param eventName The name of the event
     */
    fun logTimedEventStart(eventName: String)

    /**
     * Log a timed event (end)
     *
     * This calculates the duration since logTimedEventStart and logs the event.
     *
     * @param eventName The name of the event (must match start event name)
     * @param params Optional parameters to attach to the event
     */
    fun logTimedEventEnd(
        eventName: String,
        params: Map<String, Any> = emptyMap(),
    )
}
