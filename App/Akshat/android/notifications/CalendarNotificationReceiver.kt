package com.kashyap.wizlyai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for handling calendar notification alarms
 * Triggered by AlarmManager at exact notification times
 */
class CalendarNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_EVENT_TIME = "event_time"
        const val EXTRA_EVENT_DATE = "event_date"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_DAYS_UNTIL = "days_until"
        const val EXTRA_HOURS_UNTIL = "hours_until"
        const val EXTRA_NOTIFICATION_TIME = "notification_time"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: return
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: return

        val eventTime = intent.getStringExtra(EXTRA_EVENT_TIME) ?: ""
        val eventDate = intent.getStringExtra(EXTRA_EVENT_DATE) ?: ""
        val notificationType = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: return
        val daysUntil = intent.getIntExtra(EXTRA_DAYS_UNTIL, 0)
        val hoursUntil = intent.getIntExtra(EXTRA_HOURS_UNTIL, 0)
        val notificationTime = intent.getStringExtra(EXTRA_NOTIFICATION_TIME) ?: ""

        // Format notification message (same wording as iOS, from the shared scheduler)
        val type = runCatching { com.kashyap.wizlyai.data.models.NotificationType.valueOf(notificationType) }.getOrNull()
        val eventKind = com.kashyap.wizlyai.data.models.EventType.fromName(eventType)
        val title =
            if (type != null) {
                com.kashyap.wizlyai.notifications.NotificationScheduler.countdownTitle(eventKind, type, daysUntil, hoursUntil)
            } else {
                eventKind.label
            }

        val body =
            buildString {
                append(eventTitle)
                if (eventTime.isNotEmpty()) append(" at $eventTime")
            }

        // Get notification manager and show notification
        val notificationManager = CalendarNotificationManager(context)
        notificationManager.showNotification(
            eventId = eventId,
            title = title,
            body = body,
            notificationTime = notificationTime,
        )
    }
}
