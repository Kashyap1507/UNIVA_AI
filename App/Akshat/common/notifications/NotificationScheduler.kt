package com.kashyap.wizlyai.notifications

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.models.EventType
import com.kashyap.wizlyai.data.models.NotificationSettings
import com.kashyap.wizlyai.data.models.NotificationTime
import com.kashyap.wizlyai.data.models.NotificationType
import kotlinx.datetime.*

/**
 * Platform-agnostic notification scheduling logic
 * Calculates when notifications should be sent based on event time and user settings
 */
object NotificationScheduler {
    /**
     * Calculate all notification times for a calendar event
     *
     * @param event The calendar event to schedule notifications for
     * @param settings User's notification preferences
     * @param currentTime Current timestamp (for testing and validation)
     * @return List of scheduled notification times
     */
    fun calculateNotificationTimes(
        event: CalendarEvent,
        settings: NotificationSettings,
        currentTime: Instant = Clock.System.now(),
    ): List<NotificationTime> {
        if (!settings.enableCalendarNotifications) {
            return emptyList()
        }

        val notifications = mutableListOf<NotificationTime>()

        // Parse event date and time
        val eventDateTime = parseEventDateTime(event) ?: return emptyList()

        // Validate event is in the future
        if (eventDateTime.compareTo(currentTime) <= 0) {
            return emptyList()
        }

        // Calculate notification range
        // Start time: Beginning of day N days before event (not exact event time)
        val timeZone = TimeZone.currentSystemDefault()
        val eventLocalDate = eventDateTime.toLocalDateTime(timeZone).date
        val startDate = eventLocalDate.minus(settings.startDaysBefore, DateTimeUnit.DAY)
        val startTime = LocalDateTime(startDate, LocalTime(0, 0)).toInstant(timeZone)

        // Stop time: X hours before event (exact time)
        val stopTime = eventDateTime.minus(settings.stopHoursBefore.toLong(), DateTimeUnit.HOUR, timeZone)

        // Ensure start time is in the future
        val effectiveStartTime = if (startTime.compareTo(currentTime) > 0) startTime else currentTime

        if (effectiveStartTime.compareTo(stopTime) >= 0) {
            return emptyList()
        }

        // Generate daily notifications (from start to event day)
        notifications.addAll(
            generateDailyNotifications(
                event = event,
                eventDateTime = eventDateTime,
                startTime = effectiveStartTime,
                stopTime = stopTime,
                dailyTime = settings.dailyNotificationTime,
            ),
        )

        // Generate hourly notifications (on event day)
        notifications.addAll(
            generateHourlyNotifications(
                event = event,
                eventDateTime = eventDateTime,
                stopTime = stopTime,
                hourlyStartHoursBefore = settings.hourlyStartHoursBefore,
                hourlyIntervalHours = settings.hourlyIntervalHours,
            ),
        )

        // Sort by time and remove duplicates
        return notifications
            .sortedBy { it.dateTime }
            .distinctBy { it.dateTime }
    }

    /**
     * Generate daily countdown notifications
     * Sent at a specific time each day from startDaysBefore until event day
     */
    private fun generateDailyNotifications(
        event: CalendarEvent,
        eventDateTime: Instant,
        startTime: Instant,
        stopTime: Instant,
        dailyTime: com.kashyap.wizlyai.data.models.TimeOfDay,
    ): List<NotificationTime> {
        val notifications = mutableListOf<NotificationTime>()
        val timeZone = TimeZone.currentSystemDefault()

        // Get the date portion of start and event
        val startDate = startTime.toLocalDateTime(timeZone).date
        val eventDate = eventDateTime.toLocalDateTime(timeZone).date

        // Calculate how many full days we have for daily notifications
        var currentDate = startDate

        while (currentDate < eventDate) {
            // Create notification time for this day at the specified time
            val notificationDateTime =
                LocalDateTime(
                    date = currentDate,
                    time = LocalTime(dailyTime.hour, dailyTime.minute),
                )

            val notificationInstant = notificationDateTime.toInstant(timeZone)

            // Only add if within valid range and in the future
            if (notificationInstant.compareTo(startTime) >= 0 &&
                notificationInstant.compareTo(stopTime) <= 0 &&
                notificationInstant.compareTo(Clock.System.now()) > 0
            ) {
                val daysUntil = currentDate.daysUntil(eventDate)

                notifications.add(
                    NotificationTime(
                        dateTime = notificationInstant.toString(),
                        type = NotificationType.DAILY_COUNTDOWN,
                        daysUntil = daysUntil,
                        hoursUntil = null,
                    ),
                )
            }

            // Move to next day
            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
        }

        return notifications
    }

    /**
     * Generate hourly countdown notifications
     * Sent every N hours starting X hours before the event on event day
     */
    private fun generateHourlyNotifications(
        event: CalendarEvent,
        eventDateTime: Instant,
        stopTime: Instant,
        hourlyStartHoursBefore: Int,
        hourlyIntervalHours: Int,
    ): List<NotificationTime> {
        val notifications = mutableListOf<NotificationTime>()
        val timeZone = TimeZone.currentSystemDefault()

        // Start hourly notifications X hours before event
        val hourlyStartTime = eventDateTime.minus(hourlyStartHoursBefore.toLong(), DateTimeUnit.HOUR, timeZone)

        // Generate notifications at intervals
        var currentTime = hourlyStartTime

        while (currentTime.compareTo(stopTime) < 0 && currentTime.compareTo(eventDateTime) < 0) {
            // Only add if in the future
            if (currentTime.compareTo(Clock.System.now()) > 0) {
                val hoursUntil = ((eventDateTime - currentTime).inWholeHours).toInt()

                notifications.add(
                    NotificationTime(
                        dateTime = currentTime.toString(),
                        type = NotificationType.HOURLY_COUNTDOWN,
                        daysUntil = null,
                        hoursUntil = hoursUntil,
                    ),
                )
            }

            // Move to next interval
            currentTime = currentTime.plus(hourlyIntervalHours.toLong(), DateTimeUnit.HOUR, timeZone)
        }

        return notifications
    }

    /**
     * Countdown title shared by Android and iOS, e.g. "Exam tomorrow", "Assignment in 3 days"
     */
    fun countdownTitle(
        eventType: EventType,
        notificationType: NotificationType,
        daysUntil: Int?,
        hoursUntil: Int?,
    ): String {
        val label = eventType.label
        return when (notificationType) {
            NotificationType.DAILY_COUNTDOWN -> {
                val days = daysUntil ?: 0
                if (days == 1) "$label tomorrow" else "$label in $days days"
            }
            NotificationType.HOURLY_COUNTDOWN -> {
                val hours = hoursUntil ?: 0
                if (hours == 1) "$label in 1 hour" else "$label in $hours hours"
            }
        }
    }

    /**
     * Format notification message based on type and time until event
     */
    fun formatNotificationMessage(
        event: CalendarEvent,
        notificationTime: NotificationTime,
    ): NotificationMessage {
        val title = countdownTitle(event.type, notificationTime.type, notificationTime.daysUntil, notificationTime.hoursUntil)
        val body =
            buildString {
                append(event.title)
                event.time?.let { append(" at $it") }
            }
        return NotificationMessage(
            title = title,
            body = body,
            eventId = event.id ?: "",
        )
    }

    /**
     * Parse event date and optional time into an Instant
     */
    private fun parseEventDateTime(event: CalendarEvent): Instant? {
        return try {
            val timeZone = TimeZone.currentSystemDefault()

            // Parse date (ISO format: YYYY-MM-DD)
            val localDate = LocalDate.parse(event.date)

            // Parse time if available (HH:MM format)
            val localTime =
                if (!event.time.isNullOrEmpty()) {
                    val parts = event.time.split(":")
                    LocalTime(parts[0].toInt(), parts[1].toInt())
                } else {
                    // Default to 9:00 AM if no time specified
                    LocalTime(9, 0)
                }

            val localDateTime = LocalDateTime(localDate, localTime)
            localDateTime.toInstant(timeZone)
        } catch (e: Exception) {
            println("❌ Failed to parse event date/time: ${e.message}")
            null
        }
    }
}

/**
 * Formatted notification message
 */
data class NotificationMessage(
    val title: String,
    val body: String,
    val eventId: String,
)
