package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.SseEvent
import com.kashyap.wizlyai.network.SseEventParser
import com.kashyap.wizlyai.network.models.CreateWorkSubmissionRequest
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.WorkStreamEvent
import com.kashyap.wizlyai.network.models.WorkSubmission
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** "Check my work" submissions and their progress stream. */
class WorkRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json

    suspend fun createSubmission(documentId: String, courseId: String, isPractice: Boolean): Result<WorkSubmission> =
        call(WorkSubmission.serializer()) {
            client.post("${baseUrl()}/work/submissions") {
                contentType(ContentType.Application.Json)
                setBody(CreateWorkSubmissionRequest(documentId, courseId, isPractice))
            }
        }

    suspend fun getSubmission(id: String): Result<WorkSubmission> =
        call(WorkSubmission.serializer()) { client.get("${baseUrl()}/work/submissions/$id") }

    suspend fun listSubmissions(courseId: String? = null): Result<List<WorkSubmission>> =
        call(ListSerializer(WorkSubmission.serializer())) {
            client.get("${baseUrl()}/work/submissions") { courseId?.let { parameter("course_id", it) } }
        }

    /** Progress events until done or error. Never throws. */
    fun stream(id: String): Flow<WorkStreamEvent> =
        flow {
            try {
                client.prepareGet("${baseUrl()}/work/submissions/$id/stream") {
                    accept(ContentType.Text.EventStream)
                    timeout {
                        requestTimeoutMillis = STREAM_TIMEOUT_MS
                        socketTimeoutMillis = STREAM_TIMEOUT_MS
                    }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        emit(WorkStreamEvent.Failed("Couldn't follow this check (${response.status.value})"))
                        return@execute
                    }
                    val channel = response.bodyAsChannel()
                    val parser = SseEventParser()
                    var finished = false
                    while (!channel.isClosedForRead && !finished) {
                        val line = channel.readUTF8Line(MAX_LINE_CHARS) ?: break
                        val event = parser.feed(line)?.let { toEvent(it) } ?: continue
                        emit(event)
                        finished = event !is WorkStreamEvent.Step
                    }
                    if (!finished) emit(WorkStreamEvent.Failed("The connection dropped. Pull to refresh to see the result."))
                }
            } catch (e: Exception) {
                println("⚠️ WorkRepository: stream failed: ${e.message}")
                emit(WorkStreamEvent.Failed("Can't reach the server. Check your connection and try again."))
            }
        }

    fun toEvent(event: SseEvent): WorkStreamEvent? =
        try {
            when (event.event) {
                "step" -> WorkStreamEvent.Step(json.parseToJsonElement(event.data).jsonObject["step"]?.jsonPrimitive?.content.orEmpty())
                "done" -> WorkStreamEvent.Done(json.decodeFromString(WorkSubmission.serializer(), event.data))
                "error" ->
                    WorkStreamEvent.Failed(
                        json.parseToJsonElement(event.data).jsonObject["message"]?.jsonPrimitive?.content ?: "Couldn't check this work.",
                    )
                else -> null
            }
        } catch (e: Exception) {
            null
        }

    private suspend fun <T> call(serializer: KSerializer<T>, request: suspend () -> HttpResponse): Result<T> =
        try {
            val response = request()
            val envelope = json.decodeFromString(Envelope.serializer(serializer), response.bodyAsText())
            val data = envelope.data
            if (!response.status.isSuccess() || !envelope.success || data == null) {
                Result.failure(CourseApiException(envelope.error ?: "Request failed (${response.status.value})", response.status.value))
            } else {
                Result.success(data)
            }
        } catch (e: Exception) {
            println("⚠️ WorkRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }

    companion object {
        const val STREAM_TIMEOUT_MS = 330_000L
        private const val MAX_LINE_CHARS = 1_000_000
    }
}
