package com.kashyap.wizlyai.util

import kotlinx.datetime.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Date/Time formatting utilities for document timestamps
 */
object DateTimeFormatter {
    /**
     * Format ISO 8601 timestamp to "YYYY-MM-DD HH:MM" format
     *
     * @param isoString ISO 8601 timestamp (e.g., "2025-10-12T14:02:35Z")
     * @return Formatted string "2025-10-12 14:02" or original if parsing fails
     */
    fun formatDocumentDateTime(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

            val year = localDateTime.year
            val month = localDateTime.monthNumber.toString().padStart(2, '0')
            val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
            val hour = localDateTime.hour.toString().padStart(2, '0')
            val minute = localDateTime.minute.toString().padStart(2, '0')

            "$year-$month-$day $hour:$minute"
        } catch (e: Exception) {
            // Fallback to first 16 characters if parsing fails
            isoString.take(16).replace('T', ' ')
        }
    }

    /**
     * Format timestamp with relative time for recent documents
     * Shows "2 hours ago", "Yesterday", etc. for recent documents
     * Shows full date/time for older documents
     *
     * @param isoString ISO 8601 timestamp
     * @return Formatted string with relative or absolute time
     */
    fun formatRelativeDateTime(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val now = Clock.System.now()
            val duration = now - instant

            when {
                duration < 1.hours -> {
                    val minutes = duration.inWholeMinutes
                    if (minutes < 1) "Just now" else "$minutes min ago"
                }
                duration < 24.hours -> {
                    val hours = duration.inWholeHours
                    "$hours hour${if (hours > 1) "s" else ""} ago"
                }
                duration < 2.days -> "Yesterday"
                duration < 7.days -> {
                    val days = duration.inWholeDays
                    "$days days ago"
                }
                else -> formatDocumentDateTime(isoString)
            }
        } catch (e: Exception) {
            formatDocumentDateTime(isoString)
        }
    }

    /**
     * Generate default document title with current timestamp
     * Format: "New Doc - YYYY-MM-DD HH:MM:SS"
     *
     * This is the unified title format used across:
     * - Photo Review Screen
     * - Document Viewer Screen
     * - Scanning Flow
     *
     * @return Default document title with current timestamp
     */
    fun generateDefaultDocumentTitle(): String {
        return try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val year = now.year
            val month = now.monthNumber.toString().padStart(2, '0')
            val day = now.dayOfMonth.toString().padStart(2, '0')
            val hour = now.hour.toString().padStart(2, '0')
            val minute = now.minute.toString().padStart(2, '0')
            val second = now.second.toString().padStart(2, '0')
            "New Doc - $year-$month-$day $hour:$minute:$second"
        } catch (e: Exception) {
            "New Doc - ${Clock.System.now().toEpochMilliseconds()}"
        }
    }

    /**
     * Format just the date portion (YYYY-MM-DD)
     *
     * @param isoString ISO 8601 timestamp
     * @return Date string "2025-10-12"
     */
    fun formatDate(isoString: String): String {
        return isoString.take(10)
    }

    /**
     * Format just the time portion (HH:MM)
     *
     * @param isoString ISO 8601 timestamp
     * @return Time string "14:02"
     */
    fun formatTime(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

            val hour = localDateTime.hour.toString().padStart(2, '0')
            val minute = localDateTime.minute.toString().padStart(2, '0')

            "$hour:$minute"
        } catch (e: Exception) {
            isoString.substring(11, 16)
        }
    }
}
