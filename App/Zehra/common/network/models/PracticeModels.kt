package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class PracticeKind(val apiName: String, val label: String) {
    PRACTICE("PRACTICE", "Practice set"),
    LECTURE_QUIZ("LECTURE_QUIZ", "Lecture quiz"),
    MOCK_EXAM("MOCK_EXAM", "Mock exam"),
    ;

    companion object {
        fun from(value: String?): PracticeKind = entries.firstOrNull { it.apiName == value } ?: PRACTICE
    }
}

/** A question as the student sees it while answering (no answer or explanation). */
@Serializable
data class ExamQuestion(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("options") val options: List<String>? = null,
    @SerialName("unit") val unit: String? = null,
    @SerialName("points") val points: Double = 1.0,
    @SerialName("concept_name") val conceptName: String? = null,
) {
    val isChoice: Boolean get() = type == "MCQ"
    val isNumeric: Boolean get() = type == "NUMERIC"
    val isWritten: Boolean get() = type == "SHORT" || type == "PROOF" || type == "CODE"
}

@Serializable
data class PracticeSet(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("kind") val kind: String,
    @SerialName("title") val title: String? = null,
    @SerialName("status") val status: String = "PROCESSING",
    @SerialName("error") val error: String? = null,
    @SerialName("time_limit_min") val timeLimitMin: Int? = null,
    @SerialName("question_count") val questionCount: Int = 0,
    @SerialName("total_points") val totalPoints: Double = 0.0,
    @SerialName("best_score_pct") val bestScorePct: Double? = null,
    @SerialName("attempt_count") val attemptCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("questions") val questions: List<ExamQuestion>? = null,
) {
    val practiceKind: PracticeKind get() = PracticeKind.from(kind)
    val isReady: Boolean get() = status == "READY"
    val isProcessing: Boolean get() = status == "PROCESSING"
}

@Serializable
data class GeneratePracticeRequest(
    @SerialName("kind") val kind: String,
    @SerialName("count") val count: Int? = null,
    @SerialName("module_ids") val moduleIds: List<String>? = null,
    @SerialName("time_limit_min") val timeLimitMin: Int? = null,
)

@Serializable
data class PracticeAnswer(
    @SerialName("question_id") val questionId: String,
    /** Option index (MCQ) or text. */
    @SerialName("response") val response: JsonElement = JsonNull,
)

@Serializable
data class SubmitPracticeRequest(
    @SerialName("answers") val answers: List<PracticeAnswer>,
)

@Serializable
data class ConceptResult(
    @SerialName("concept_id") val conceptId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("points") val points: Double = 0.0,
    @SerialName("max_points") val maxPoints: Double = 0.0,
    @SerialName("correct") val correct: Int = 0,
    @SerialName("total") val total: Int = 0,
) {
    val fraction: Float get() = if (maxPoints > 0) (points / maxPoints).toFloat() else 0f
}

@Serializable
data class QuestionResult(
    @SerialName("status") val status: String? = null,
    @SerialName("correct") val correct: Boolean? = null,
    @SerialName("points_awarded") val pointsAwarded: Double? = null,
    @SerialName("feedback") val feedback: String? = null,
) {
    val isGraded: Boolean get() = status == "GRADED"
}

/** A question after submission: the answer, explanation and how the student did. */
@Serializable
data class ReviewedQuestion(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("options") val options: List<String>? = null,
    @SerialName("unit") val unit: String? = null,
    @SerialName("points") val points: Double = 1.0,
    @SerialName("concept_name") val conceptName: String? = null,
    @SerialName("correct_option") val correctOption: Int? = null,
    @SerialName("numeric_answer") val numericAnswer: Double? = null,
    @SerialName("model_answer") val modelAnswer: String? = null,
    @SerialName("key_points") val keyPoints: List<String>? = null,
    @SerialName("explanation") val explanation: String? = null,
    @SerialName("source") val source: FlashcardSource? = null,
    @SerialName("response") val response: JsonElement? = null,
    @SerialName("result") val result: QuestionResult = QuestionResult(),
) {
    val responseText: String?
        get() =
            when {
                response == null || response is JsonNull -> null
                type == "MCQ" -> response.jsonPrimitive.intOrNull?.let { options?.getOrNull(it) }
                else -> (response as? JsonPrimitive)?.content
            }
}

@Serializable
data class PracticeAttempt(
    @SerialName("id") val id: String,
    @SerialName("set_id") val setId: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("kind") val kind: String,
    @SerialName("title") val title: String? = null,
    @SerialName("status") val status: String = "IN_PROGRESS",
    @SerialName("points") val points: Double? = null,
    @SerialName("max_points") val maxPoints: Double? = null,
    @SerialName("score_pct") val scorePct: Double? = null,
    @SerialName("per_concept") val perConcept: Map<String, ConceptResult> = emptyMap(),
    @SerialName("ungraded") val ungraded: Int = 0,
    @SerialName("late") val late: Boolean = false,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("time_limit_min") val timeLimitMin: Int? = null,
    @SerialName("questions") val questions: List<ExamQuestion>? = null,
    @SerialName("review") val review: List<ReviewedQuestion>? = null,
)
