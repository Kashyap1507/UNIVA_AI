package com.kashyap.wizlyai.network.models

import com.kashyap.wizlyai.srs.SrsState
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FlashcardSource(
    @SerialName("chunk_id") val chunkId: String? = null,
    @SerialName("material_id") val materialId: String? = null,
    @SerialName("material_title") val materialTitle: String? = null,
    @SerialName("page") val page: Int? = null,
) {
    val label: String? get() = materialTitle?.takeIf { it.isNotBlank() }?.let { t -> page?.let { "$t, p. $it" } ?: t }
}

@Serializable
data class FlashcardSrs(
    @SerialName("ease") val ease: Double = 2.5,
    @SerialName("interval_days") val intervalDays: Int = 0,
    @SerialName("reps") val reps: Int = 0,
    @SerialName("lapses") val lapses: Int = 0,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null,
) {
    val state: SrsState get() = SrsState(ease, intervalDays, reps, lapses)

    val dueInstant: Instant? get() = dueAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    val isNew: Boolean get() = reps == 0 && lastReviewedAt == null
}

@Serializable
data class Flashcard(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("concept_id") val conceptId: String? = null,
    @SerialName("front") val front: String,
    @SerialName("back") val back: String,
    @SerialName("origin") val origin: String = "MANUAL",
    @SerialName("source") val source: FlashcardSource? = null,
    @SerialName("suspended") val suspended: Boolean = false,
    @SerialName("srs") val srs: FlashcardSrs = FlashcardSrs(),
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** Due now or earlier (a missing date counts as due). */
    fun isDue(now: Instant): Boolean = !suspended && (srs.dueInstant?.let { it <= now } ?: true)
}

@Serializable
data class GenerateFlashcardsRequest(
    @SerialName("count") val count: Int,
    @SerialName("concept_id") val conceptId: String? = null,
)

@Serializable
data class FlashcardEditRequest(
    @SerialName("front") val front: String? = null,
    @SerialName("back") val back: String? = null,
    @SerialName("suspended") val suspended: Boolean? = null,
)

@Serializable
data class FlashcardReviewRequest(
    @SerialName("grade") val grade: Int,
    @SerialName("reviewed_at") val reviewedAt: String,
    @SerialName("client_review_id") val clientReviewId: String,
)

@Serializable
data class FlashcardSummary(
    @SerialName("due_total") val dueTotal: Int = 0,
    @SerialName("due_by_course") val dueByCourse: Map<String, Int> = emptyMap(),
)

/** A review answered on the device and not yet confirmed by the server. */
@Serializable
data class PendingReview(
    @SerialName("card_id") val cardId: String,
    @SerialName("grade") val grade: Int,
    @SerialName("reviewed_at") val reviewedAt: String,
    @SerialName("client_review_id") val clientReviewId: String,
)
