package com.kashyap.wizlyai.data.models

import kotlinx.serialization.Serializable

/**
 * Notification settings for calendar events
 * Supports continuous notification range from X days before to Y hours before event
 */
@Serializable
data class NotificationSettings(
    // Global enable/disable
    val enableCalendarNotifications: Boolean = true,
    // Notification Range
    val startDaysBefore: Int = 7, // Start notifications X days before (1-30)
    val stopHoursBefore: Int = 2, // Stop notifications Y hours before (1-24) - minimum threshold
    // Daily Notifications (during countdown days)
    val dailyNotificationTime: TimeOfDay = TimeOfDay(18, 0), // 6:00 PM
    // Hourly Notifications (on event day)
    val hourlyStartHoursBefore: Int = 6, // Start hourly notifications 6 hours before event
    val hourlyIntervalHours: Int = 2, // Send every 2 hours (1-12)
) {
    /**
     * Validate settings
     */
    fun isValid(): Boolean {
        return startDaysBefore in 1..30 &&
            stopHoursBefore in 1..24 &&
            hourlyStartHoursBefore > stopHoursBefore &&
            hourlyIntervalHours in 1..12 &&
            dailyNotificationTime.isValid()
    }

    companion object {
        val DEFAULT = NotificationSettings()
    }
}

/**
 * Time of day (24-hour format)
 */
@Serializable
data class TimeOfDay(
    val hour: Int, // 0-23
    val minute: Int, // 0-59
) {
    /**
     * Format as 24-hour time
     */
    fun format24Hour(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    /**
     * Format as 12-hour time with AM/PM
     */
    fun format12Hour(): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour =
            when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
        return "$displayHour:${minute.toString().padStart(2, '0')} $period"
    }

    /**
     * Validate time is valid
     */
    fun isValid(): Boolean = hour in 0..23 && minute in 0..59

    /**
     * Convert to total minutes since midnight
     */
    fun toMinutesSinceMidnight(): Int = hour * 60 + minute

    companion object {
        fun fromMinutesSinceMidnight(minutes: Int): TimeOfDay {
            val hour = (minutes / 60) % 24
            val minute = minutes % 60
            return TimeOfDay(hour, minute)
        }
    }
}

/**
 * Represents a scheduled notification time
 */
@Serializable
data class NotificationTime(
    val dateTime: String, // ISO 8601 format
    val type: NotificationType,
    val daysUntil: Int? = null, // For daily countdown
    val hoursUntil: Int? = null, // For hourly countdown
)

/**
 * Type of notification
 */
@Serializable
enum class NotificationType {
    DAILY_COUNTDOWN, // "X days until..." - sent at configured daily time
    HOURLY_COUNTDOWN, // "X hours until..." - sent every N hours on event day
}
