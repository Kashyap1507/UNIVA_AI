package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TutorSession(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String? = null,
    @SerialName("title") val title: String = "New conversation",
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** A course source the tutor cited as [n]. */
@Serializable
data class TutorCitation(
    @SerialName("n") val n: Int,
    @SerialName("chunk_id") val chunkId: String? = null,
    @SerialName("material_id") val materialId: String,
    @SerialName("title") val title: String,
    @SerialName("page") val page: Int? = null,
    @SerialName("page_end") val pageEnd: Int? = null,
) {
    val pageLabel: String?
        get() =
            when {
                page == null -> null
                pageEnd != null && pageEnd != page -> "pp. $page–$pageEnd"
                else -> "p. $page"
            }
}

@Serializable
data class TutorMessage(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("citations") val citations: List<TutorCitation> = emptyList(),
    @SerialName("grounded") val grounded: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isUser: Boolean get() = role == "user"
}

@Serializable
data class TutorSessionDetail(
    @SerialName("session") val session: TutorSession,
    @SerialName("messages") val messages: List<TutorMessage> = emptyList(),
)

@Serializable
data class TutorDonePayload(
    @SerialName("message") val message: TutorMessage,
    @SerialName("session") val session: TutorDoneSession? = null,
)

@Serializable
data class TutorDoneSession(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String? = null,
)

@Serializable
data class SendTutorMessageRequest(
    @SerialName("content") val content: String,
)

/** Events from POST /api/tutor/sessions/<id>/messages (SSE). */
sealed class TutorStreamEvent {
    data class Started(val userMessageId: String?) : TutorStreamEvent()

    data class Sources(val grounded: Boolean, val count: Int) : TutorStreamEvent()

    data class Delta(val text: String) : TutorStreamEvent()

    data class Done(val payload: TutorDonePayload) : TutorStreamEvent()

    data class Failed(val message: String) : TutorStreamEvent()
}
