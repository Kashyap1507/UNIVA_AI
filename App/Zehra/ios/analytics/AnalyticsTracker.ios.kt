package com.kashyap.wizlyai.analytics

import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of AnalyticsTracker (NO-OP - Firebase temporarily disabled)
 *
 * TEMPORARY: Firebase Analytics removed due to Xcode 16.4/Kotlin incompatibility.
 * This is a stub implementation that logs to console only.
 *
 * TODO: Re-enable Firebase Analytics when Kotlin/Native adds Xcode 16.4 support
 *       OR integrate alternative analytics (Firebase Swift SDK, Sentry, etc.)
 *
 * Architecture:
 * - Currently logs analytics events to NSLog for debugging
 * - No actual analytics data is collected on iOS
 * - Android implementation remains fully functional with Firebase
 */
actual object AnalyticsTracker {
    private val timedEvents = mutableMapOf<String, Double>()

    /**
     * Log a custom analytics event (NO-OP)
     */
    actual fun logEvent(
        eventName: String,
        params: Map<String, Any>,
    ) {
        NSLog("📊 [Analytics-STUB] Event: $eventName - Params: $params")
    }

    /**
     * Log a screen view event (NO-OP)
     */
    actual fun logScreenView(
        screenName: String,
        screenClass: String?,
    ) {
        NSLog("📱 [Analytics-STUB] Screen View: $screenName ${screenClass?.let { "($it)" } ?: ""}")
    }

    /**
     * Set the user ID for analytics (NO-OP)
     */
    actual fun setUserId(userId: String?) {
        NSLog("👤 [Analytics-STUB] User ID Set: ${userId ?: "null"}")
    }

    /**
     * Set a user property (NO-OP)
     */
    actual fun setUserProperty(
        name: String,
        value: String,
    ) {
        NSLog("🏷️ [Analytics-STUB] User Property: $name = $value")
    }

    /**
     * Enable or disable analytics collection (NO-OP)
     */
    actual fun setAnalyticsEnabled(enabled: Boolean) {
        NSLog("🔒 [Analytics-STUB] Analytics ${if (enabled) "Enabled" else "Disabled"}")
    }

    /**
     * Log app open event (NO-OP)
     */
    actual fun logAppOpen() {
        NSLog("🚀 [Analytics-STUB] App Open")
    }

    /**
     * Log a timed event (start)
     */
    actual fun logTimedEventStart(eventName: String) {
        timedEvents[eventName] = NSDate().timeIntervalSince1970
        NSLog("⏱️ [Analytics-STUB] Timed Event Started: $eventName")
    }

    /**
     * Log a timed event (end)
     */
    actual fun logTimedEventEnd(
        eventName: String,
        params: Map<String, Any>,
    ) {
        val startTime = timedEvents.remove(eventName)
        if (startTime == null) {
            NSLog("⚠️ [Analytics-STUB] No start time found for timed event: $eventName")
            return
        }

        val durationMs = ((NSDate().timeIntervalSince1970 - startTime) * 1000).toLong()

        val enrichedParams = params.toMutableMap()
        enrichedParams[AnalyticsParams.DURATION_MS] = durationMs

        NSLog("⏱️ [Analytics-STUB] Timed Event Ended: $eventName (${durationMs}ms) - Params: $enrichedParams")
    }
}

/**
 * iOS-specific extension functions for Swift interop
 *
 * These functions can be called from SwiftUI views to track analytics.
 * They will be exposed to Swift code via the shared framework.
 */

/**
 * Track screen view from SwiftUI
 */
fun trackScreenView(screenName: String) {
    AnalyticsTracker.logScreenView(screenName)
}

/**
 * Track event from SwiftUI
 */
fun trackEvent(
    eventName: String,
    params: Map<String, Any> = emptyMap(),
) {
    AnalyticsTracker.logEvent(eventName, params)
}
