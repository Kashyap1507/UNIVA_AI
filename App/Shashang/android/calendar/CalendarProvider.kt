package com.kashyap.wizlyai.calendar

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.models.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.resume

/**
 * Android implementation of CalendarProvider
 * Uses Android CalendarContract to access device calendars
 */
actual class CalendarProvider(
    private val context: Context,
    private val activity: Activity? = null,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val REQUEST_CODE_CALENDAR = 1001
        var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    }

    actual suspend fun requestPermission(): Boolean {
        // If already has permission, return true
        if (hasPermission()) {
            return true
        }

        // If no activity provided, cannot request permission
        if (activity == null) {
            android.util.Log.w("CalendarProvider", "Cannot request permission - Activity not provided")
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            pendingPermissionCallback = { granted ->
                continuation.resume(granted)
                pendingPermissionCallback = null
            }

            // Request permission from Activity
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_CALENDAR),
                REQUEST_CODE_CALENDAR,
            )
        }
    }

    /**
     * Call this from Activity.onRequestPermissionsResult
     */
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_CODE_CALENDAR) {
            val granted =
                grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingPermissionCallback?.invoke(granted)
        }
    }

    actual fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun getDeviceCalendars(type: CalendarType): List<DeviceCalendar> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) {
                throw IllegalStateException("Calendar permission not granted")
            }

            val calendars = mutableListOf<DeviceCalendar>()

            val projection =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.IS_PRIMARY,
                )

            val cursor: Cursor? =
                contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
                )

            cursor?.use {
                val idIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val colorIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                val isPrimaryIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex)
                    val accountName = it.getString(accountNameIndex) ?: "Unknown"
                    val accountType = it.getString(accountTypeIndex) ?: "Unknown"
                    val color = it.getInt(colorIndex)
                    val isPrimary = it.getInt(isPrimaryIndex) == 1

                    // Filter by calendar type
                    val shouldInclude =
                        when (type) {
                            CalendarType.GOOGLE -> accountType.contains("com.google", ignoreCase = true)
                            CalendarType.APPLE ->
                                accountType.contains("icloud", ignoreCase = true) ||
                                    accountType.contains("caldav", ignoreCase = true)
                            CalendarType.ALL -> true
                        }

                    if (shouldInclude) {
                        calendars.add(
                            DeviceCalendar(
                                id = id,
                                name = name,
                                accountName = accountName,
                                accountType = accountType,
                                color = color,
                                isPrimary = isPrimary,
                            ),
                        )
                    }
                }
            }

            calendars
        }

    actual suspend fun getEvents(
        calendarIds: List<String>,
        startDate: String,
        endDate: String,
        filterStudyRelated: Boolean,
    ): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) {
                throw IllegalStateException("Calendar permission not granted")
            }

            val events = mutableListOf<CalendarEvent>()

            // Parse date strings
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)

            // Convert to milliseconds for Android Calendar API
            val startMillis = start.toEpochDays() * 24 * 60 * 60 * 1000L
            val endMillis = (end.toEpochDays() + 1) * 24 * 60 * 60 * 1000L

            val projection =
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.CALENDAR_ID,
                )

            val selection =
                "${CalendarContract.Events.CALENDAR_ID} IN (${calendarIds.joinToString(",")}) AND " +
                    "${CalendarContract.Events.DTSTART} >= ? AND " +
                    "${CalendarContract.Events.DTSTART} <= ? AND " +
                    "${CalendarContract.Events.DELETED} = 0"

            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

            val cursor: Cursor? =
                contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC",
                )

            cursor?.use {
                val idIndex = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descriptionIndex = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIndex = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIndex = it.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIndex = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val locationIndex = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val calendarIdIndex = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)

                while (it.moveToNext()) {
                    // Extract device event ID and calendar ID for deduplication
                    val deviceEventId = it.getLong(idIndex).toString()
                    val deviceCalendarId = it.getString(calendarIdIndex)

                    val title = it.getString(titleIndex) ?: continue
                    val description = it.getString(descriptionIndex)
                    val startTimeMillis = it.getLong(startIndex)
                    val endTimeMillis = it.getLong(endIndex)
                    val isAllDay = it.getInt(allDayIndex) == 1
                    val location = it.getString(locationIndex)

                    // Apply study-related filter
                    if (filterStudyRelated && !StudyEventKeywords.isStudyRelated(title, description)) {
                        continue
                    }

                    // Convert to LocalDateTime
                    val startDateTime = convertMillisToLocalDateTime(startTimeMillis)
                    val eventDate = "${startDateTime.year}-${startDateTime.monthNumber.toString().padStart(
                        2,
                        '0',
                    )}-${startDateTime.dayOfMonth.toString().padStart(2, '0')}"
                    val eventTime =
                        if (!isAllDay) {
                            "${startDateTime.hour.toString().padStart(2, '0')}:${startDateTime.minute.toString().padStart(2, '0')}"
                        } else {
                            null
                        }

                    // Determine event type based on keywords
                    val eventType = determineEventType(title, description)

                    events.add(
                        CalendarEvent(
                            id = null, // Not saved yet
                            title = title,
                            date = eventDate,
                            time = eventTime,
                            type = eventType,
                            description = description,
                            durationMinutes =
                                if (endTimeMillis > 0) {
                                    ((endTimeMillis - startTimeMillis) / 60000).toInt()
                                } else {
                                    null
                                },
                            source = "device_calendar_synced",
                            deviceEventId = deviceEventId, // For deduplication on sync
                            calendarId = deviceCalendarId, // Which calendar it came from
                            lastSyncedAt = kotlinx.datetime.Clock.System.now().toString(), // Current sync timestamp
                        ),
                    )
                }
            }

            events
        }

    private fun convertMillisToLocalDateTime(millis: Long): LocalDateTime {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        return instant.toLocalDateTime(TimeZone.currentSystemDefault())
    }

    private fun determineEventType(
        title: String,
        description: String?,
    ): EventType {
        val text = "$title ${description ?: ""}".lowercase()

        return EventType.infer(text)
    }
}
