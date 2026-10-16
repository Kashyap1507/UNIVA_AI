package com.kashyap.wizlyai.notifications

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS implementation of CalendarNotificationManagerBridge
 * Bridges to Swift IOSCalendarNotificationManager via NotificationCallbacks
 * Singleton object
 */
actual object CalendarNotificationManagerBridge {
    /**
     * No-op on iOS (initialization handled by Swift)
     */
    actual fun initialize(context: Any) {
        // Not needed on iOS
    }

    /**
     * No-op on iOS (no activity concept)
     */
    actual fun setActivity(activity: Any?) {
        // Not needed on iOS
    }

    actual fun scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager,
    ) {
        val callbacks = IOSCallbackBridge.getNotificationCallbacks() ?: return

        try {
            callbacks.scheduleNotifications(event, preferencesManager)
        } catch (e: Exception) {
            // Silently fail
        }
    }

    actual fun cancelNotifications(eventId: String) {
        val callbacks = IOSCallbackBridge.getNotificationCallbacks() ?: return

        try {
            callbacks.cancelNotifications(eventId)
        } catch (e: Exception) {
            // Silently fail
        }
    }

    actual fun cancelAllNotifications() {
        val callbacks = IOSCallbackBridge.getNotificationCallbacks() ?: return

        try {
            callbacks.cancelAllNotifications()
        } catch (e: Exception) {
            // Silently fail
        }
    }

    actual fun hasPermission(): Boolean {
        val callbacks = IOSCallbackBridge.getNotificationCallbacks() ?: return false

        var hasPermission = false
        callbacks.hasPermission { granted ->
            hasPermission = granted
        }
        return hasPermission
    }

    actual fun requestPermission() {
        val callbacks = IOSCallbackBridge.getNotificationCallbacks() ?: return

        try {
            callbacks.requestPermission { _ -> }
        } catch (e: Exception) {
            // Silently fail
        }
    }

    /**
     * No-op on iOS (test notifications are Android-only)
     */
    actual fun sendTestNotification() {
        // Not supported on iOS
    }

    /**
     * Always returns true on iOS (no exact alarm permission needed)
     */
    actual fun canScheduleExactAlarms(): Boolean {
        return true // iOS doesn't require exact alarm permission
    }

    /**
     * No-op on iOS (no exact alarm permission needed)
     */
    actual fun requestExactAlarmPermission() {
        // Not needed on iOS
    }
}
