package com.kashyap.wizlyai.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.models.NotificationTime
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.data.preferences.getNotificationSettings
import kotlinx.datetime.Instant

/**
 * Android notification manager for calendar event notifications
 * Handles notification channel creation, scheduling, and display
 */
class CalendarNotificationManager(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "calendar_notifications"
        private const val CHANNEL_NAME = "Calendar Events"
        private const val CHANNEL_DESCRIPTION = "Notifications for upcoming calendar events"

        private const val ALARM_REQUEST_CODE_PREFIX = 1000000

        // Notification IDs are generated from event ID hash
        private fun getNotificationId(
            eventId: String,
            notificationTime: String,
        ): Int {
            return "$eventId-$notificationTime".hashCode()
        }

        // Alarm request codes are generated from event ID hash
        private fun getAlarmRequestCode(
            eventId: String,
            notificationTime: String,
        ): Int {
            return ALARM_REQUEST_CODE_PREFIX + getNotificationId(eventId, notificationTime)
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannel()
    }

    /**
     * Create notification channel for calendar events (Android O+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                    setShowBadge(true)
                }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedule all notifications for a calendar event
     */
    fun scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager,
    ) {
        val settings = preferencesManager.getNotificationSettings()

        // Calculate notification times using the shared algorithm
        val notificationTimes =
            NotificationScheduler.calculateNotificationTimes(
                event = event,
                settings = settings,
            )

        if (notificationTimes.isEmpty()) {
            return
        }

        // Schedule each notification using AlarmManager
        notificationTimes.forEach { notificationTime ->
            scheduleNotification(event, notificationTime)
        }
    }

    /**
     * Schedule a single notification using AlarmManager
     */
    private fun scheduleNotification(
        event: CalendarEvent,
        notificationTime: NotificationTime,
    ) {
        val eventId = event.id ?: return

        // Parse notification time
        val notificationInstant =
            try {
                Instant.parse(notificationTime.dateTime)
            } catch (e: Exception) {
                return
            }

        // Calculate trigger time in milliseconds
        val triggerAtMillis = notificationInstant.toEpochMilliseconds()
        val currentTimeMillis = System.currentTimeMillis()

        if (triggerAtMillis <= currentTimeMillis) {
            return
        }

        // Create intent for BroadcastReceiver with notification data
        val intent =
            Intent(context, CalendarNotificationReceiver::class.java).apply {
                putExtra(CalendarNotificationReceiver.EXTRA_EVENT_ID, eventId)
                putExtra(CalendarNotificationReceiver.EXTRA_EVENT_TITLE, event.title)
                putExtra(CalendarNotificationReceiver.EXTRA_EVENT_TYPE, event.type.name)
                putExtra(CalendarNotificationReceiver.EXTRA_EVENT_TIME, event.time ?: "")
                putExtra(CalendarNotificationReceiver.EXTRA_EVENT_DATE, event.date)
                putExtra(CalendarNotificationReceiver.EXTRA_NOTIFICATION_TYPE, notificationTime.type.name)
                putExtra(CalendarNotificationReceiver.EXTRA_DAYS_UNTIL, notificationTime.daysUntil ?: 0)
                putExtra(CalendarNotificationReceiver.EXTRA_HOURS_UNTIL, notificationTime.hoursUntil ?: 0)
                putExtra(CalendarNotificationReceiver.EXTRA_NOTIFICATION_TIME, notificationTime.dateTime)
            }

        val requestCode = getAlarmRequestCode(eventId, notificationTime.dateTime)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // Schedule exact alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires canScheduleExactAlarms() check
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    // Fall back to inexact alarm if permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            // Silently fail if alarm scheduling is not permitted
        }
    }

    /**
     * Cancel all notifications for a specific event
     * Note: This is a best-effort cancellation. For complete cancellation tracking,
     * alarm request codes should be persisted.
     */
    fun cancelNotifications(eventId: String) {
        // Cancel all possible notification alarms for this event
        // Since we can't query AlarmManager, we try common request code patterns
        val requestCodeBase = getAlarmRequestCode(eventId, "")
        for (i in 0 until 100) { // Try up to 100 possible alarms per event
            try {
                val requestCode = requestCodeBase + i
                val intent = Intent(context, CalendarNotificationReceiver::class.java)
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            } catch (e: Exception) {
                // Ignore errors during cancellation
            }
        }
    }

    /**
     * Cancel all calendar notifications
     * Note: This is a best-effort cancellation since we can't enumerate all alarms
     */
    fun cancelAllNotifications() {
        // Try to cancel alarms in the typical request code range
        for (requestCode in ALARM_REQUEST_CODE_PREFIX until ALARM_REQUEST_CODE_PREFIX + 10000) {
            try {
                val intent = Intent(context, CalendarNotificationReceiver::class.java)
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            } catch (e: Exception) {
                // Ignore errors during cancellation
            }
        }
    }

    /**
     * Test notification - fires immediately to verify notification system works
     */
    fun sendTestNotification() {
        if (!hasNotificationPermission()) {
            return
        }

        val testTitle = "🧪 Test Notification"
        val testBody = "If you see this, notifications are working! ✅"
        val testEventId = "test_notification_${System.currentTimeMillis()}"

        showNotification(
            eventId = testEventId,
            title = testTitle,
            body = testBody,
            notificationTime = kotlinx.datetime.Clock.System.now().toString(),
        )
    }

    /**
     * Display a notification immediately (used by Worker)
     */
    fun showNotification(
        eventId: String,
        title: String,
        body: String,
        notificationTime: String,
    ) {
        val notificationId = getNotificationId(eventId, notificationTime)

        // Create intent to open app when notification is tapped
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("eventId", eventId)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // Build notification
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar) // TODO: Replace with app icon
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        // Show notification
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            println("❌ Permission denied to show notification: ${e.message}")
        }
    }

    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationManager.areNotificationsEnabled()
        } else {
            true // No runtime permission needed before Android 13
        }
    }

    /**
     * Check if the app can schedule exact alarms (Android 12+)
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No permission needed before Android 12
        }
    }

    /**
     * Request exact alarm permission (Android 12+)
     * Opens system settings where user can grant permission
     */
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent =
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                println("❌ Failed to open exact alarm settings: ${e.message}")
            }
        }
    }
}
