package com.kashyap.wizlyai.utils

import kotlinx.datetime.*
import kotlin.time.Duration

/**
 * Utility for formatting timestamps as relative time strings
 *
 * Examples:
 * - "today"
 * - "2 hours ago"
 * - "3 days ago"
 * - "Oct 21"
 * - "Jan 15, 2024"
 */
object RelativeTimeFormatter {
    /**
     * Format an ISO timestamp as a relative time string
     *
     * @param timestamp ISO 8601 timestamp string (e.g., "2025-10-22T10:30:00Z")
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: String): String {
        return try {
            val instant = Instant.parse(timestamp)
            val now = Clock.System.now()
            val duration = now - instant

            formatDuration(instant, duration, now)
        } catch (e: Exception) {
            // Fallback to simple date extraction if parsing fails
            formatFallback(timestamp)
        }
    }

    private fun formatDuration(
        instant: Instant,
        duration: Duration,
        now: Instant,
    ): String {
        val seconds = duration.inWholeSeconds
        val minutes = duration.inWholeMinutes
        val hours = duration.inWholeHours
        val days = duration.inWholeDays

        return when {
            // Future dates (should not happen, but handle gracefully)
            seconds < 0 -> "just now"

            // Today (less than 24 hours ago)
            hours < 1 && minutes < 1 -> "just now"
            hours < 1 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            hours < 24 && isSameDay(instant, now) -> "$hours hour${if (hours == 1L) "" else "s"} ago"

            // Yesterday
            days == 1L && isYesterday(instant, now) -> "yesterday"

            // This week (2-6 days ago)
            days < 7 -> "$days day${if (days == 1L) "" else "s"} ago"

            // This year (show month and day)
            isSameYear(instant, now) -> formatMonthDay(instant)

            // Previous years (show month, day, year)
            else -> formatMonthDayYear(instant)
        }
    }

    private fun isSameDay(
        instant1: Instant,
        instant2: Instant,
    ): Boolean {
        val tz = TimeZone.currentSystemDefault()
        val date1 = instant1.toLocalDateTime(tz).date
        val date2 = instant2.toLocalDateTime(tz).date
        return date1 == date2
    }

    private fun isYesterday(
        instant: Instant,
        now: Instant,
    ): Boolean {
        val tz = TimeZone.currentSystemDefault()
        val date = instant.toLocalDateTime(tz).date
        val today = now.toLocalDateTime(tz).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        return date == yesterday
    }

    private fun isSameYear(
        instant1: Instant,
        instant2: Instant,
    ): Boolean {
        val tz = TimeZone.currentSystemDefault()
        val year1 = instant1.toLocalDateTime(tz).year
        val year2 = instant2.toLocalDateTime(tz).year
        return year1 == year2
    }

    private fun formatMonthDay(instant: Instant): String {
        val tz = TimeZone.currentSystemDefault()
        val dateTime = instant.toLocalDateTime(tz)
        val monthName = getMonthAbbreviation(dateTime.month)
        return "$monthName ${dateTime.dayOfMonth}"
    }

    private fun formatMonthDayYear(instant: Instant): String {
        val tz = TimeZone.currentSystemDefault()
        val dateTime = instant.toLocalDateTime(tz)
        val monthName = getMonthAbbreviation(dateTime.month)
        return "$monthName ${dateTime.dayOfMonth}, ${dateTime.year}"
    }

    private fun getMonthAbbreviation(month: Month): String {
        return when (month) {
            Month.JANUARY -> "Jan"
            Month.FEBRUARY -> "Feb"
            Month.MARCH -> "Mar"
            Month.APRIL -> "Apr"
            Month.MAY -> "May"
            Month.JUNE -> "Jun"
            Month.JULY -> "Jul"
            Month.AUGUST -> "Aug"
            Month.SEPTEMBER -> "Sep"
            Month.OCTOBER -> "Oct"
            Month.NOVEMBER -> "Nov"
            Month.DECEMBER -> "Dec"
            else -> month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatFallback(timestamp: String): String {
        return try {
            // Try to extract date part from ISO string
            val datePart = timestamp.substringBefore('T')
            val parts = datePart.split('-')
            if (parts.size == 3) {
                val year = parts[0].toIntOrNull() ?: return timestamp
                val month = parts[1].toIntOrNull()?.let { Month(it) } ?: return timestamp
                val day = parts[2].toIntOrNull() ?: return timestamp

                val now = Clock.System.now()
                val currentYear = now.toLocalDateTime(TimeZone.currentSystemDefault()).year

                val monthName = getMonthAbbreviation(month)

                if (year == currentYear) {
                    "$monthName $day"
                } else {
                    "$monthName $day, $year"
                }
            } else {
                timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }
}
