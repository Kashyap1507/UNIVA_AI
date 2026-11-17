package com.kashyap.wizlyai.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a calendar event (test, assignment, or general event)
 */
@Serializable
data class CalendarEvent(
    val id: String? = null, // Null for extracted (unsaved) events
    val title: String,
    val date: String, // ISO date format (YYYY-MM-DD)
    val time: String? = null, // HH:mm format
    val type: EventType,
    val description: String? = null,
    @kotlinx.serialization.SerialName("duration_minutes")
    val durationMinutes: Int? = null, // Duration in minutes
    val priority: String? = null, // low, medium, high
    @kotlinx.serialization.SerialName("reminder_enabled")
    val reminderEnabled: Boolean = true,
    val source: String = "manual", // manual, synced
    @kotlinx.serialization.SerialName("created_at")
    val createdAt: String? = null,
    @kotlinx.serialization.SerialName("updated_at")
    val updatedAt: String? = null,
    // Device calendar sync fields (for deduplication)
    @kotlinx.serialization.SerialName("device_event_id")
    val deviceEventId: String? = null, // Unique ID from device calendar (Android: _ID, iOS: eventIdentifier)
    @kotlinx.serialization.SerialName("calendar_id")
    val calendarId: String? = null, // Which calendar it came from
    @kotlinx.serialization.SerialName("last_synced_at")
    val lastSyncedAt: String? = null, // Last sync timestamp (ISO format)
    // Soft delete flag
    @kotlinx.serialization.SerialName("is_deleted")
    val isDeleted: Boolean = false, // Soft delete flag (true = deleted, false = active)
    // Completion status
    @kotlinx.serialization.SerialName("is_completed")
    val isCompleted: Boolean = false, // Completion flag (true = completed, false = upcoming)
    @kotlinx.serialization.SerialName("completed_at")
    val completedAt: String? = null, // ISO timestamp when event was marked as completed
)
