package com.kashyap.wizlyai.notifications

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.preferences.PreferencesManager

/**
 * Platform-agnostic bridge for calendar notification management
 * Uses expect/actual pattern for platform-specific implementations
 * Singleton object pattern
 */
expect object CalendarNotificationManagerBridge {
    /**
     * Initialize the notification manager (Android only)
     */
    fun initialize(context: Any)

    /**
     * Set activity reference for permission requests (Android only)
     */
    fun setActivity(activity: Any?)

    /**
     * Schedule notifications for a calendar event
     */
    fun scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager,
    )

    /**
     * Cancel all notifications for an event
     */
    fun cancelNotifications(eventId: String)

    /**
     * Cancel all calendar notifications
     */
    fun cancelAllNotifications()

    /**
     * Check if notification permission is granted
     */
    fun hasPermission(): Boolean

    /**
     * Request notification permission (returns immediately, handled by platform)
     */
    fun requestPermission()

    /**
     * Send a test notification immediately (for debugging)
     * Android-only, no-op on iOS
     */
    fun sendTestNotification()

    /**
     * Check if exact alarm permission is granted (Android 12+ only)
     * Returns true on older versions or iOS
     */
    fun canScheduleExactAlarms(): Boolean

    /**
     * Request exact alarm permission (Android 12+ only)
     * Opens settings where user can grant permission
     * No-op on older versions or iOS
     */
    fun requestExactAlarmPermission()
}
