package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** University course (`/api/courses`). */
@Serializable
data class Course(
    @SerialName("id") val id: String,
    @SerialName("semester_id") val semesterId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("code") val code: String? = null,
    @SerialName("instructor") val instructor: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("exam_dates") val examDates: List<String> = emptyList(),
    @SerialName("status") val status: String = "ACTIVE",
    @SerialName("description") val description: String? = null,
    @SerialName("structure_status") val structureStatus: String = "NONE",
    @SerialName("structure_error") val structureError: String? = null,
    @SerialName("material_count") val materialCount: Int? = null,
    @SerialName("module_count") val moduleCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isArchived: Boolean get() = status == "ARCHIVED"
    val isGeneratingOutline: Boolean get() = structureStatus == "PROCESSING"
    val displayTitle: String get() = if (code.isNullOrBlank()) name else "$code · $name"
}

@Serializable
data class Semester(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
)

enum class IngestionStatus { PENDING, PROCESSING, READY, FAILED }

@Serializable
data class CourseMaterial(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("kind") val kind: String = "PDF",
    @SerialName("title") val title: String,
    @SerialName("mime") val mime: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("pages") val pages: Int? = null,
    @SerialName("ingestion_status") val ingestionStatus: String = "PENDING",
    @SerialName("error") val error: String? = null,
    @SerialName("chunk_count") val chunkCount: Int = 0,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Lecture summary (Phase 10), once the student asked for one. */
    @SerialName("summary") val summary: MaterialSummary? = null,
) {
    val status: IngestionStatus
        get() = IngestionStatus.entries.firstOrNull { it.name == ingestionStatus } ?: IngestionStatus.PENDING

    val isInProgress: Boolean
        get() = status == IngestionStatus.PENDING || status == IngestionStatus.PROCESSING
}

@Serializable
data class CourseModule(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("title") val title: String,
    @SerialName("order") val order: Int = 0,
    @SerialName("week") val week: Int? = null,
    @SerialName("summary") val summary: String? = null,
)

@Serializable
data class CourseConcept(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("module_id") val moduleId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("summary") val summary: String? = null,
    @SerialName("learning_outcome") val learningOutcome: String? = null,
    @SerialName("mastery") val mastery: Double = 0.0,
)

@Serializable
data class CourseSearchResult(
    @SerialName("chunk_id") val chunkId: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("material_title") val materialTitle: String,
    @SerialName("text") val text: String,
    @SerialName("page") val page: Int? = null,
    @SerialName("page_end") val pageEnd: Int? = null,
    @SerialName("score") val score: Double? = null,
)

/** Standard `{success, data, error}` envelope used by the course endpoints. */
@Serializable
data class Envelope<T>(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: T? = null,
    @SerialName("error") val error: String? = null,
)

/** New course from the "Add course" form. */
@Serializable
data class CreateCourseRequest(
    @SerialName("name") val name: String,
    @SerialName("code") val code: String? = null,
    @SerialName("instructor") val instructor: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("exam_dates") val examDates: List<String> = emptyList(),
    @SerialName("semester_id") val semesterId: String? = null,
)
