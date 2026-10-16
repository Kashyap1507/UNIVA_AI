package com.kashyap.wizlyai.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.preferences.PreferencesManager

/**
 * Android implementation of CalendarNotificationManagerBridge
 * Singleton object for managing calendar notifications
 */
actual object CalendarNotificationManagerBridge {
    private var notificationManager: CalendarNotificationManager? = null
    private var appContext: android.content.Context? = null
    private var currentActivity: android.app.Activity? = null
    private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    /**
     * Initialize with application context (call from MainActivity)
     */
    actual fun initialize(context: Any) {
        if (context is android.content.Context) {
            appContext = context.applicationContext
        }
    }

    /**
     * Set the current activity for permission requests
     */
    actual fun setActivity(activity: Any?) {
        currentActivity = activity as? android.app.Activity
    }

    private fun getNotificationManager(): CalendarNotificationManager? {
        if (notificationManager == null && appContext != null) {
            notificationManager = CalendarNotificationManager(appContext!!)
        }
        return notificationManager
    }

    actual fun scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager,
    ) {
        getNotificationManager()?.scheduleNotifications(event, preferencesManager)
    }

    actual fun cancelNotifications(eventId: String) {
        getNotificationManager()?.cancelNotifications(eventId)
    }

    actual fun cancelAllNotifications() {
        getNotificationManager()?.cancelAllNotifications()
    }

    actual fun hasPermission(): Boolean {
        val context = appContext ?: return false

        // Android 13+ requires POST_NOTIFICATIONS permission
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No runtime permission needed before Android 13
        }
    }

    actual fun requestPermission() {
        // Request permission from Activity if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentActivity?.let { activity ->
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE,
                )
            }
        }
    }

    /**
     * Send a test notification immediately to verify the system works
     */
    actual fun sendTestNotification() {
        getNotificationManager()?.sendTestNotification()
    }

    /**
     * Check if exact alarm permission is granted (Android 12+)
     */
    actual fun canScheduleExactAlarms(): Boolean {
        return getNotificationManager()?.canScheduleExactAlarms() ?: false
    }

    /**
     * Request exact alarm permission (Android 12+)
     * Opens system settings where user can grant permission
     */
    actual fun requestExactAlarmPermission() {
        getNotificationManager()?.requestExactAlarmPermission()
    }
}
