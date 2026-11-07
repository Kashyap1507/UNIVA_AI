package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.SseEvent
import com.kashyap.wizlyai.network.SseEventParser
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.SendTutorMessageRequest
import com.kashyap.wizlyai.network.models.TutorDonePayload
import com.kashyap.wizlyai.network.models.TutorSession
import com.kashyap.wizlyai.network.models.TutorSessionDetail
import com.kashyap.wizlyai.network.models.TutorStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Tutor sessions and streamed answers (course tutor endpoints under /api/courses/<id>/tutor and /api/tutor). */
class TutorRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json

    suspend fun listSessions(courseId: String): Result<List<TutorSession>> =
        call(ListSerializer(TutorSession.serializer())) { client.get("${baseUrl()}/courses/$courseId/tutor/sessions") }

    suspend fun createSession(courseId: String): Result<TutorSession> =
        call(TutorSession.serializer()) { client.post("${baseUrl()}/courses/$courseId/tutor/sessions") }

    suspend fun getSession(sessionId: String): Result<TutorSessionDetail> =
        call(TutorSessionDetail.serializer()) { client.get("${baseUrl()}/tutor/sessions/$sessionId") }

    suspend fun deleteSession(sessionId: String): Result<Unit> =
        call(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/tutor/sessions/$sessionId") }.map { }

    /** Ask a question; emits events as the answer streams in. Never throws. */
    fun ask(sessionId: String, question: String): Flow<TutorStreamEvent> =
        flow {
            try {
                client.preparePost("${baseUrl()}/tutor/sessions/$sessionId/messages") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Text.EventStream)
                    setBody(SendTutorMessageRequest(question))
                    timeout {
                        requestTimeoutMillis = STREAM_TIMEOUT_MS
                        socketTimeoutMillis = STREAM_TIMEOUT_MS
                    }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        emit(TutorStreamEvent.Failed(errorMessage(response.bodyAsText(), response.status.value)))
                        return@execute
                    }
                    val channel = response.bodyAsChannel()
                    val parser = SseEventParser()
                    var finished = false
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line(MAX_LINE_CHARS) ?: break
                        val event = parser.feed(line) ?: continue
                        val mapped = toStreamEvent(event) ?: continue
                        emit(mapped)
                        if (mapped is TutorStreamEvent.Done || mapped is TutorStreamEvent.Failed) finished = true
                    }
                    parser.flush()?.let { toStreamEvent(it) }?.let {
                        emit(it)
                        finished = true
                    }
                    if (!finished) emit(TutorStreamEvent.Failed("The answer was cut off. Please try again."))
                }
            } catch (e: Exception) {
                println("⚠️ TutorRepository: stream failed: ${e.message}")
                emit(TutorStreamEvent.Failed("Can't reach the tutor. Check your connection and try again."))
            }
        }

    fun toStreamEvent(event: SseEvent): TutorStreamEvent? =
        try {
            when (event.event) {
                "start" -> TutorStreamEvent.Started(json.parseToJsonElement(event.data).jsonObject["user_message_id"]?.jsonPrimitive?.content)
                "sources" -> {
                    val obj = json.parseToJsonElement(event.data).jsonObject
                    TutorStreamEvent.Sources(
                        grounded = obj["grounded"]?.jsonPrimitive?.booleanOrNull ?: true,
                        count = obj["count"]?.jsonPrimitive?.intOrNull ?: 0,
                    )
                }
                "delta" -> TutorStreamEvent.Delta(json.parseToJsonElement(event.data).jsonObject["text"]?.jsonPrimitive?.content.orEmpty())
                "done" -> TutorStreamEvent.Done(json.decodeFromString(TutorDonePayload.serializer(), event.data))
                "error" ->
                    TutorStreamEvent.Failed(
                        json.parseToJsonElement(event.data).jsonObject["message"]?.jsonPrimitive?.content
                            ?: "The tutor couldn't answer right now.",
                    )
                else -> null
            }
        } catch (e: Exception) {
            println("⚠️ TutorRepository: bad event ${event.event}: ${e.message}")
            null
        }

    private fun errorMessage(body: String, status: Int): String =
        try {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content ?: "Request failed ($status)"
        } catch (e: Exception) {
            "Request failed ($status)"
        }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> call(
        serializer: KSerializer<T>,
        allowEmptyData: Boolean = false,
        request: suspend () -> HttpResponse,
    ): Result<T> =
        try {
            val response = request()
            val text = response.bodyAsText()
            val envelope = json.decodeFromString(Envelope.serializer(serializer), text)
            if (!response.status.isSuccess() || !envelope.success) {
                Result.failure(CourseApiException(envelope.error ?: "Request failed (${response.status.value})", response.status.value))
            } else {
                Result.success(envelope.data ?: if (allowEmptyData) JsonObject(emptyMap()) as T else throw CourseApiException("Empty response"))
            }
        } catch (e: CourseApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            println("⚠️ TutorRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }

    companion object {
        const val STREAM_TIMEOUT_MS = 180_000L
        private const val MAX_LINE_CHARS = 1_000_000
    }
}
