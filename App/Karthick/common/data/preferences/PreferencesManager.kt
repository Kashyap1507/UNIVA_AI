package com.kashyap.wizlyai.data.preferences

import com.kashyap.wizlyai.data.models.NotificationSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Platform-agnostic preferences manager for storing user settings
 * Uses expect/actual pattern for platform-specific implementations
 */
expect class PreferencesManager {
    /**
     * Save a boolean preference
     */
    fun putBoolean(
        key: String,
        value: Boolean,
    )

    /**
     * Get a boolean preference
     */
    fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean

    /**
     * Save a string preference
     */
    fun putString(
        key: String,
        value: String,
    )

    /**
     * Get a string preference
     */
    fun getString(
        key: String,
        defaultValue: String,
    ): String

    /**
     * Save a long preference
     */
    fun putLong(
        key: String,
        value: Long,
    )

    /**
     * Get a long preference
     */
    fun getLong(
        key: String,
        defaultValue: Long,
    ): Long

    /**
     * Remove a preference
     */
    fun remove(key: String)

    /**
     * Clear all preferences
     */
    fun clear()
}

/**
 * Preference keys
 */
object PreferenceKeys {
    // User preferences
    const val PUSH_NOTIFICATIONS = "push_notifications"
    const val PRACTICE_REMINDERS = "practice_reminders"
    const val APP_THEME_MODE = "app_theme_mode"
    const val APP_THEME_ID = "app_theme_id"
    const val EMAIL_NOTIFICATIONS = "email_notifications"

    // Authentication & session
    const val AUTH_TOKEN = "auth_token"
    const val USER_ID = "user_id"
    const val USER_EMAIL = "user_email"
    const val AUTH_PROVIDER = "auth_provider"
    const val TOKEN_EXPIRES_AT = "token_expires_at"
    const val HAS_SEEN_ONBOARDING = "has_seen_onboarding"

    // University student state
    const val STUDENT_NEEDS_ONBOARDING = "student_needs_onboarding"

    // Developer settings
    const val SHOW_DEBUG_INFO_OVERLAY = "show_debug_info_overlay"
    const val USE_PRODUCTION_SERVER = "use_production_server"
    const val CUSTOM_SERVER_IP = "custom_server_ip"

    // Notification settings
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    const val NOTIFICATION_FIRST_LAUNCH_SHOWN = "notification_first_launch_shown"
}

/**
 * Extension functions for notification settings
 */
fun PreferencesManager.saveNotificationSettings(settings: NotificationSettings) {
    val json = Json.encodeToString(settings)
    putString(PreferenceKeys.NOTIFICATION_SETTINGS, json)
}

fun PreferencesManager.getNotificationSettings(): NotificationSettings {
    val json = getString(PreferenceKeys.NOTIFICATION_SETTINGS, "")
    return if (json.isNotEmpty()) {
        try {
            Json.decodeFromString<NotificationSettings>(json)
        } catch (e: Exception) {
            NotificationSettings.DEFAULT
        }
    } else {
        NotificationSettings.DEFAULT
    }
}

fun PreferencesManager.hasRequestedNotificationPermission(): Boolean {
    return getBoolean(PreferenceKeys.NOTIFICATION_PERMISSION_REQUESTED, false)
}

fun PreferencesManager.setNotificationPermissionRequested(requested: Boolean) {
    putBoolean(PreferenceKeys.NOTIFICATION_PERMISSION_REQUESTED, requested)
}

fun PreferencesManager.hasShownFirstLaunchNotificationDialog(): Boolean {
    return getBoolean(PreferenceKeys.NOTIFICATION_FIRST_LAUNCH_SHOWN, false)
}

fun PreferencesManager.setFirstLaunchNotificationDialogShown(shown: Boolean) {
    putBoolean(PreferenceKeys.NOTIFICATION_FIRST_LAUNCH_SHOWN, shown)
}
