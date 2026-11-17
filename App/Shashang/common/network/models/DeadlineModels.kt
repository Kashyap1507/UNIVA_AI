package com.kashyap.wizlyai.network.models

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DeadlineType(val apiName: String, val label: String) {
    EXAM("EXAM", "Exam"),
    ASSIGNMENT("ASSIGNMENT", "Assignment"),
    QUIZ("QUIZ", "Quiz"),
    LAB("LAB", "Lab"),
    PRESENTATION("PRESENTATION", "Presentation"),
    OTHER("OTHER", "Other"),
    ;

    companion object {
        fun from(value: String?): DeadlineType = entries.firstOrNull { it.apiName == value } ?: OTHER
    }
}

@Serializable
data class Deadline(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String = "OTHER",
    /** "YYYY-MM-DD" (all day) or a UTC date-time "YYYY-MM-DDTHH:MM:SSZ". */
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("all_day") val allDay: Boolean = true,
    @SerialName("weight_pct") val weightPct: Double? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("source") val source: String = "MANUAL",
    @SerialName("completed") val completed: Boolean = false,
) {
    val deadlineType: DeadlineType get() = DeadlineType.from(type)

    /** The due date in the device's time zone. */
    fun localDate(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate? =
        dueAt?.let { due ->
            if (allDay || due.length == 10) {
                runCatching { LocalDate.parse(due.take(10)) }.getOrNull()
            } else {
                runCatching { Instant.parse(due).toLocalDateTime(zone).date }.getOrNull()
            }
        }

    /** "14:00" for timed deadlines, null for all-day ones. */
    fun localTime(zone: TimeZone = TimeZone.currentSystemDefault()): String? =
        dueAt?.takeIf { !allDay && it.length > 10 }?.let { due ->
            runCatching {
                val t = Instant.parse(due).toLocalDateTime(zone)
                "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
            }.getOrNull()
        }
}

@Serializable
data class DeadlineInput(
    @SerialName("title") val title: String,
    @SerialName("type") val type: String,
    @SerialName("due_at") val dueAt: String?,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("weight_pct") val weightPct: Double? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class DeadlineUpdate(
    @SerialName("title") val title: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("weight_pct") val weightPct: Double? = null,
    @SerialName("completed") val completed: Boolean? = null,
)

@Serializable
data class BulkDeadlinesRequest(
    @SerialName("source") val source: String,
    @SerialName("deadlines") val deadlines: List<DeadlineInput>,
)

@Serializable
data class SyllabusCandidate(
    @SerialName("title") val title: String,
    @SerialName("type") val type: String = "OTHER",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("weight_pct") val weightPct: Double? = null,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("duplicate_of") val duplicateOf: String? = null,
) {
    fun toInput() = DeadlineInput(title = title, type = type, dueAt = dueAt, courseId = courseId, weightPct = weightPct)
}

@Serializable
data class SyllabusPreview(
    @SerialName("material_id") val materialId: String,
    @SerialName("material_title") val materialTitle: String? = null,
    @SerialName("candidates") val candidates: List<SyllabusCandidate> = emptyList(),
)

@Serializable
data class IcalImportResult(
    @SerialName("created") val created: Int = 0,
    @SerialName("updated") val updated: Int = 0,
    @SerialName("unchanged") val unchanged: Int = 0,
    @SerialName("unmatched") val unmatched: Int = 0,
    @SerialName("total") val total: Int = 0,
)

@Serializable
data class StudyPlanTask(
    @SerialName("id") val id: String,
    /** LEARN, REVIEW_CARDS, PRACTICE, MOCK_EXAM or REVIEW. */
    @SerialName("type") val type: String,
    @SerialName("ref_id") val refId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("minutes") val minutes: Int = 0,
    @SerialName("done") val done: Boolean = false,
)

@Serializable
data class StudyPlanDay(
    @SerialName("date") val date: String,
    @SerialName("tasks") val tasks: List<StudyPlanTask> = emptyList(),
)

@Serializable
data class StudyPlan(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("deadline_id") val deadlineId: String? = null,
    @SerialName("exam_date") val examDate: String,
    @SerialName("title") val title: String,
    @SerialName("minutes_per_day") val minutesPerDay: Int = 60,
    @SerialName("tasks_total") val tasksTotal: Int = 0,
    @SerialName("tasks_done") val tasksDone: Int = 0,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("days") val days: List<StudyPlanDay>? = null,
)

@Serializable
data class GeneratePlanRequest(
    @SerialName("deadline_id") val deadlineId: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("exam_date") val examDate: String? = null,
    @SerialName("minutes_per_day") val minutesPerDay: Int,
    @SerialName("start_date") val startDate: String,
)

@Serializable
data class TaskDoneRequest(
    @SerialName("done") val done: Boolean,
)

@Serializable
data class FeedUrlRequest(
    @SerialName("url") val url: String,
)
