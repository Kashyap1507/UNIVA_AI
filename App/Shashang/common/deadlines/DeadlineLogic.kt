package com.kashyap.wizlyai.deadlines

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.models.EventType
import com.kashyap.wizlyai.network.models.Deadline
import com.kashyap.wizlyai.network.models.DeadlineInput
import com.kashyap.wizlyai.network.models.DeadlineType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

enum class DeadlineSection(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    THIS_WEEK("Next 7 days"),
    LATER("Later"),
    NO_DATE("No date yet"),
    DONE("Completed"),
}

/** Pure helpers for the Deadlines screen (unit-tested in commonTest). */
object DeadlineLogic {
    /** An open deadline as a reminder event for the notification scheduler; null if done or undated. */
    fun toReminderEvent(
        deadline: Deadline,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): CalendarEvent? {
        if (deadline.completed) return null
        val date = deadline.localDate(zone) ?: return null
        val type =
            when (deadline.deadlineType) {
                DeadlineType.EXAM -> EventType.EXAM
                DeadlineType.QUIZ -> EventType.QUIZ
                DeadlineType.ASSIGNMENT -> EventType.ASSIGNMENT
                DeadlineType.LAB -> EventType.LAB
                DeadlineType.PRESENTATION -> EventType.PRESENTATION
                DeadlineType.OTHER -> EventType.OTHER
            }
        return CalendarEvent(
            id = deadline.id,
            title = deadline.title,
            date = date.toString(),
            time = deadline.localTime(zone),
            type = type,
            source = "deadline",
        )
    }

    fun section(
        deadline: Deadline,
        today: LocalDate,
        zone: TimeZone,
    ): DeadlineSection {
        if (deadline.completed) return DeadlineSection.DONE
        val date = deadline.localDate(zone) ?: return DeadlineSection.NO_DATE
        val days = today.daysUntil(date)
        return when {
            days < 0 -> DeadlineSection.OVERDUE
            days == 0 -> DeadlineSection.TODAY
            days <= 7 -> DeadlineSection.THIS_WEEK
            else -> DeadlineSection.LATER
        }
    }

    /** Sections in display order; completed items newest first, the rest soonest first. */
    fun group(
        deadlines: List<Deadline>,
        today: LocalDate,
        zone: TimeZone,
    ): List<Pair<DeadlineSection, List<Deadline>>> =
        deadlines
            .groupBy { section(it, today, zone) }
            .map { (s, items) ->
                val sorted = items.sortedBy { it.dueAt ?: "9999" }
                s to if (s == DeadlineSection.DONE) sorted.reversed() else sorted
            }
            .sortedBy { it.first.ordinal }

    /** "Today", "Tomorrow", "in 5 days", "2 days ago", "in 3 weeks". */
    fun countdown(
        date: LocalDate?,
        today: LocalDate,
    ): String? {
        date ?: return null
        val days = today.daysUntil(date)
        return when {
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days == -1 -> "Yesterday"
            days < 0 -> "${-days} days ago"
            days < 21 -> "in $days days"
            days < 70 -> "in ${(days + 3) / 7} weeks"
            else -> "in ${(days + 15) / 30} months"
        }
    }

    /** Same keyword rules as the server's iCal import (most specific first). */
    fun inferType(title: String): DeadlineType {
        val low = title.lowercase()
        return when {
            Regex("\\bquiz").containsMatchIn(low) -> DeadlineType.QUIZ
            Regex("\\bpresentation|\\bpitch\\b|\\bposter\\b").containsMatchIn(low) -> DeadlineType.PRESENTATION
            Regex("\\blab\\b|\\blaboratory\\b").containsMatchIn(low) -> DeadlineType.LAB
            Regex("\\b(exam|midterm|mid-term|final|test)\\b").containsMatchIn(low) -> DeadlineType.EXAM
            Regex("\\b(assignment|homework|hw\\d*|problem set|project|essay|report|due|submission|deliverable)\\b").containsMatchIn(low) -> DeadlineType.ASSIGNMENT
            else -> DeadlineType.OTHER
        }
    }

    /** A device calendar event as a deadline. Timed events are converted from the device zone to UTC. */
    fun fromDeviceEvent(
        event: CalendarEvent,
        zone: TimeZone,
    ): DeadlineInput? {
        val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return null
        val due =
            event.time?.let { t ->
                runCatching {
                    LocalDateTime(date, LocalTime.parse(t.take(5))).toInstant(zone).toString()
                }.getOrNull()
            } ?: date.toString()
        val type =
            when (event.type) {
                EventType.EXAM -> DeadlineType.EXAM
                EventType.QUIZ -> DeadlineType.QUIZ
                EventType.PRESENTATION -> DeadlineType.PRESENTATION
                EventType.LAB -> DeadlineType.LAB
                EventType.ASSIGNMENT, EventType.PROJECT -> DeadlineType.ASSIGNMENT
                else -> inferType(event.title)
            }
        return DeadlineInput(title = event.title.trim().take(200), type = type.apiName, dueAt = due)
    }

    /** Device events worth importing: from today on, not already saved (same title and date). */
    fun newDeviceEvents(
        events: List<CalendarEvent>,
        existing: List<Deadline>,
        today: LocalDate,
        zone: TimeZone,
    ): List<DeadlineInput> {
        val saved = existing.map { normalise(it.title) to it.localDate(zone) }.toSet()
        return events
            .mapNotNull { fromDeviceEvent(it, zone) }
            .filter { input ->
                val d = runCatching { LocalDate.parse(input.dueAt!!.take(10)) }.getOrNull()
                d != null && d >= today && (normalise(input.title) to d) !in saved
            }
            .distinctBy { normalise(it.title) to it.dueAt }
    }

    fun normalise(title: String): String = title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Plan setup: the countdown must start before the exam. */
    fun daysUntil(
        today: LocalDate,
        examDate: LocalDate,
    ): Int = today.daysUntil(examDate)

    fun addDays(
        date: LocalDate,
        days: Int,
    ): LocalDate = date.plus(DatePeriod(days = days))
}
