package com.kashyap.wizlyai.calendar

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.models.EventType
import com.kashyap.wizlyai.ios.IOSCallbackBridge
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of CalendarProvider
 * Uses callbacks to call Swift IOSCalendarManager
 */
actual class CalendarProvider {
    actual suspend fun requestPermission(): Boolean =
        suspendCoroutine { continuation ->
            val callbacks = IOSCallbackBridge.getCalendarCallbacks()
            if (callbacks == null) {
                println("⚠️ iOS CalendarProvider: Calendar callbacks not registered")
                continuation.resume(false)
                return@suspendCoroutine
            }

            callbacks.requestPermission { granted ->
                continuation.resume(granted)
            }
        }

    actual fun hasPermission(): Boolean {
        val callbacks = IOSCallbackBridge.getCalendarCallbacks() ?: return false

        var hasPermission = false
        callbacks.hasPermission { granted ->
            hasPermission = granted
        }
        return hasPermission
    }

    actual suspend fun getDeviceCalendars(type: CalendarType): List<DeviceCalendar> =
        suspendCoroutine { continuation ->
            val callbacks = IOSCallbackBridge.getCalendarCallbacks()
            if (callbacks == null) {
                println("⚠️ iOS CalendarProvider: Calendar callbacks not registered")
                continuation.resume(emptyList())
                return@suspendCoroutine
            }

            val typeString =
                when (type) {
                    CalendarType.GOOGLE -> "google"
                    CalendarType.APPLE -> "apple"
                    CalendarType.ALL -> "all"
                }

            callbacks.getDeviceCalendars(typeString) { calendarsArray ->
                val calendars =
                    calendarsArray?.map { calendarDict ->
                        val dict = calendarDict as Map<String, Any>
                        DeviceCalendar(
                            id = dict["id"] as? String ?: "",
                            name = dict["name"] as? String ?: "",
                            accountName = dict["accountName"] as? String ?: "",
                            accountType = dict["accountType"] as? String ?: "",
                            color = ((dict["color"] as? Number)?.toLong() ?: 0L).toInt(),
                            isPrimary = dict["isPrimary"] as? Boolean ?: false,
                        )
                    } ?: emptyList()

                continuation.resume(calendars)
            }
        }

    actual suspend fun getEvents(
        calendarIds: List<String>,
        startDate: String,
        endDate: String,
        filterStudyRelated: Boolean,
    ): List<CalendarEvent> =
        suspendCoroutine { continuation ->
            val callbacks = IOSCallbackBridge.getCalendarCallbacks()
            if (callbacks == null) {
                println("⚠️ iOS CalendarProvider: Calendar callbacks not registered")
                continuation.resume(emptyList())
                return@suspendCoroutine
            }

            callbacks.getEvents(calendarIds, startDate, endDate, filterStudyRelated) { eventsArray ->
                val events =
                    eventsArray?.map { eventDict ->
                        val dict = eventDict as Map<String, Any?>
                        val title = dict["title"] as? String ?: ""
                        val description = dict["description"] as? String
                        CalendarEvent(
                            id = null,
                            title = title,
                            date = dict["date"] as? String ?: "",
                            time = dict["time"] as? String,
                            type = parseEventType(dict["type"] as? String, "$title ${description.orEmpty()}"),
                            description = description,
                            durationMinutes = ((dict["durationMinutes"] as? Number)?.toLong() ?: 0L).toInt(),
                            source = "device_calendar_synced",
                            deviceEventId = dict["deviceEventId"] as? String, // For deduplication
                            calendarId = dict["calendarId"] as? String, // Which calendar it came from
                            lastSyncedAt = dict["lastSyncedAt"] as? String, // Current sync timestamp
                        )
                    } ?: emptyList()

                continuation.resume(events)
            }
        }

    private fun parseEventType(typeString: String?, text: String): EventType =
        EventType.entries.firstOrNull { it.name == typeString?.uppercase() } ?: EventType.infer(text)
}
