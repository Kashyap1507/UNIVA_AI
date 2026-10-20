package com.kashyap.wizlyai

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.preferences.PreferencesManager

/**
 * Callbacks interface for iOS notification scheduling
 * Implemented by Swift IOSCalendarNotificationManager
 */
interface NotificationCallbacks {
    /**
     * Request notification permission from user
     */
    fun requestPermission(completion: (Boolean) -> Unit)

    /**
     * Check if notification permission is granted
     */
    fun hasPermission(completion: (Boolean) -> Unit)

    /**
     * Schedule notifications for a calendar event
     *
     * @param event Calendar event to schedule notifications for
     * @param preferencesManager Preferences manager for notification settings
     */
    fun scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager,
    )

    /**
     * Cancel all notifications for a specific event
     */
    fun cancelNotifications(eventId: String)

    /**
     * Cancel all calendar notifications
     */
    fun cancelAllNotifications()
}
