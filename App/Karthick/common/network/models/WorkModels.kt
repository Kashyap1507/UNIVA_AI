package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One problem's feedback. `solution`/`correctAnswer` are only ever present for practice work. */
@Serializable
data class WorkFeedbackItem(
    @SerialName("problem") val problem: String,
    @SerialName("correct") val correct: Boolean,
    @SerialName("location") val location: String? = null,
    @SerialName("issue") val issue: String? = null,
    @SerialName("concept") val concept: String? = null,
    @SerialName("hint") val hint: String? = null,
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("solution") val solution: String? = null,
    @SerialName("correct_answer") val correctAnswer: String? = null,
)

@Serializable
data class WorkFeedback(
    @SerialName("mode") val mode: String = "HINTS",
    @SerialName("items") val items: List<WorkFeedbackItem> = emptyList(),
    @SerialName("summary") val summary: String = "",
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("verifier_notes") val verifierNotes: String? = null,
) {
    val isPractice: Boolean get() = mode == "FULL_SOLUTION"
}

@Serializable
data class WorkSubmission(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("document_id") val documentId: String? = null,
    @SerialName("is_practice") val isPractice: Boolean = false,
    @SerialName("mode") val mode: String? = null,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("step") val step: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("feedback") val feedback: WorkFeedback? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateWorkSubmissionRequest(
    @SerialName("document_id") val documentId: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("is_practice") val isPractice: Boolean,
)

sealed class WorkStreamEvent {
    data class Step(val step: String) : WorkStreamEvent()

    data class Done(val submission: WorkSubmission) : WorkStreamEvent()

    data class Failed(val message: String) : WorkStreamEvent()
}
