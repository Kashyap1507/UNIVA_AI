package com.kashyap.wizlyai.calendar

import com.kashyap.wizlyai.data.models.CalendarEvent

/**
 * Calendar type filter for device calendars
 */
enum class CalendarType {
    GOOGLE, // Google Calendar accounts
    APPLE, // Apple iCloud calendars
    ALL, // All available calendars
}

/**
 * Represents a device calendar
 */
data class DeviceCalendar(
    val id: String,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean = false,
)

/**
 * Platform-specific calendar provider
 * Provides access to device calendars (Google Calendar, Apple Calendar, etc.)
 */
expect class CalendarProvider {
    /**
     * Request calendar read permission from the user
     * @return true if permission granted, false otherwise
     */
    suspend fun requestPermission(): Boolean

    /**
     * Check if calendar permission is already granted
     * @return true if permission is granted
     */
    fun hasPermission(): Boolean

    /**
     * Get list of device calendars filtered by type
     * @param type Filter by calendar type (GOOGLE, APPLE, or ALL)
     * @return List of device calendars
     */
    suspend fun getDeviceCalendars(type: CalendarType = CalendarType.ALL): List<DeviceCalendar>

    /**
     * Get calendar events from selected calendars within date range
     * @param calendarIds List of calendar IDs to fetch from
     * @param startDate Start date in YYYY-MM-DD format
     * @param endDate End date in YYYY-MM-DD format
     * @param filterStudyRelated If true, only return study-related events
     * @return List of calendar events
     */
    suspend fun getEvents(
        calendarIds: List<String>,
        startDate: String,
        endDate: String,
        filterStudyRelated: Boolean = true,
    ): List<CalendarEvent>
}

/**
 * Keywords used to identify study-related events
 */
object StudyEventKeywords {
    val KEYWORDS =
        setOf(
            // Exams and assessments
            "test", "exam", "quiz", "assessment", "midterm", "final",
            // Coursework
            "homework", "assignment", "problem set", "project", "essay", "report", "paper",
            "presentation", "lab", "practicum", "thesis", "dissertation", "capstone",
            // Deadlines
            "due", "deadline", "submit", "submission", "turn in",
            // Classes
            "class", "lecture", "tutorial", "seminar", "workshop", "recitation",
            "office hours", "study", "review", "practice", "course",
            // Academic meetings
            "study group", "supervisor", "advisor", "defense", "viva", "colloquium",
        )

    /**
     * Check if event title or description contains study-related keywords
     */
    fun isStudyRelated(
        title: String,
        description: String?,
    ): Boolean {
        val combinedText = "$title ${description ?: ""}".lowercase()
        return KEYWORDS.any { keyword -> combinedText.contains(keyword) }
    }
}
