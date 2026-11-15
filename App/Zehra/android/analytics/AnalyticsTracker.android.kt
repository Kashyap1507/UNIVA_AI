package com.kashyap.wizlyai.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase

/**
 * Android implementation of AnalyticsTracker using Firebase Analytics
 *
 * This implementation must be initialized with an Android Context before use.
 * Initialization is typically done in MainActivity.onCreate().
 *
 * Features:
 * - Automatic screen tracking
 * - Custom event logging with parameters
 * - User ID and user property tracking
 * - Timed event measurement
 * - Privacy-respecting analytics toggle
 */
actual object AnalyticsTracker {
    private const val TAG = "AnalyticsTracker"
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val timedEvents = mutableMapOf<String, Long>()
    private var isInitialized = false

    /**
     * Initialize Firebase Analytics
     *
     * Must be called from MainActivity.onCreate() or Application.onCreate()
     *
     * @param context Android application context
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.w(TAG, "AnalyticsTracker already initialized")
            return
        }

        try {
            firebaseAnalytics = Firebase.analytics
            isInitialized = true
            Log.d(TAG, "✅ Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Firebase Analytics", e)
        }
    }

    /**
     * Check if tracker is initialized
     */
    private fun checkInitialization(): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "⚠️ AnalyticsTracker not initialized. Call initialize() first.")
            return false
        }
        return true
    }

    actual fun logEvent(
        eventName: String,
        params: Map<String, Any>,
    ) {
        if (!checkInitialization()) return

        try {
            firebaseAnalytics.logEvent(eventName) {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Int -> param(key, value.toLong())
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, if (value) 1L else 0L)
                        else -> param(key, value.toString())
                    }
                }
            }
            Log.d(TAG, "📊 Event logged: $eventName ${if (params.isNotEmpty()) "with ${params.size} params" else ""}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log event: $eventName", e)
        }
    }

    actual fun logScreenView(
        screenName: String,
        screenClass: String?,
    ) {
        if (!checkInitialization()) return

        try {
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                screenClass?.let { param(FirebaseAnalytics.Param.SCREEN_CLASS, it) }
            }
            Log.d(TAG, "📱 Screen view: $screenName${screenClass?.let { " ($it)" } ?: ""}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log screen view: $screenName", e)
        }
    }

    actual fun setUserId(userId: String?) {
        if (!checkInitialization()) return

        try {
            firebaseAnalytics.setUserId(userId)
            Log.d(TAG, "👤 User ID set: ${userId ?: "null"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set user ID", e)
        }
    }

    actual fun setUserProperty(
        name: String,
        value: String,
    ) {
        if (!checkInitialization()) return

        try {
            firebaseAnalytics.setUserProperty(name, value)
            Log.d(TAG, "🏷️ User property set: $name = $value")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set user property: $name", e)
        }
    }

    actual fun setAnalyticsEnabled(enabled: Boolean) {
        if (!checkInitialization()) return

        try {
            firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
            Log.d(TAG, "🔒 Analytics collection ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set analytics enabled state", e)
        }
    }

    actual fun logAppOpen() {
        if (!checkInitialization()) return

        try {
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
            Log.d(TAG, "🚀 App open logged")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log app open", e)
        }
    }

    actual fun logTimedEventStart(eventName: String) {
        timedEvents[eventName] = System.currentTimeMillis()
        Log.d(TAG, "⏱️ Timed event started: $eventName")
    }

    actual fun logTimedEventEnd(
        eventName: String,
        params: Map<String, Any>,
    ) {
        val startTime = timedEvents.remove(eventName)
        if (startTime == null) {
            Log.w(TAG, "⚠️ No start time found for timed event: $eventName")
            return
        }

        val duration = System.currentTimeMillis() - startTime
        val paramsWithDuration =
            params.toMutableMap().apply {
                put(AnalyticsParams.DURATION_MS, duration)
            }

        logEvent(eventName, paramsWithDuration)
        Log.d(TAG, "⏱️ Timed event ended: $eventName (${duration}ms)")
    }

    /**
     * Helper function to create a Bundle from a Map
     * (Alternative method for logging events if needed)
     */
    private fun createBundle(params: Map<String, Any>): Bundle {
        return Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
    }

    /**
     * Log API call performance
     *
     * Convenience method for tracking API performance metrics.
     *
     * @param endpoint API endpoint path
     * @param method HTTP method (GET, POST, etc.)
     * @param durationMs Request duration in milliseconds
     * @param statusCode HTTP status code
     * @param success Whether the request was successful
     */
    fun logApiCall(
        endpoint: String,
        method: String,
        durationMs: Long,
        statusCode: Int,
        success: Boolean,
    ) {
        logEvent(
            AnalyticsEvents.API_CALL,
            mapOf(
                AnalyticsParams.API_ENDPOINT to endpoint,
                AnalyticsParams.API_METHOD to method,
                AnalyticsParams.API_DURATION_MS to durationMs,
                AnalyticsParams.API_STATUS_CODE to statusCode,
                AnalyticsParams.SUCCESS to success,
            ),
        )
    }

    /**
     * Log sync performance
     *
     * Convenience method for tracking sync operations.
     *
     * @param syncType Type of sync (bootstrap, smart, document)
     * @param itemsSynced Number of items synced
     * @param durationMs Sync duration in milliseconds
     * @param success Whether sync was successful
     */
    fun logSync(
        syncType: String,
        itemsSynced: Int,
        durationMs: Long,
        success: Boolean,
    ) {
        logEvent(
            if (success) AnalyticsEvents.BOOTSTRAP_SYNC_SUCCESS else AnalyticsEvents.BOOTSTRAP_SYNC_FAILED,
            mapOf(
                AnalyticsParams.SYNC_TYPE to syncType,
                AnalyticsParams.ITEMS_SYNCED to itemsSynced,
                AnalyticsParams.DURATION_MS to durationMs,
                AnalyticsParams.SUCCESS to success,
            ),
        )
    }
}
