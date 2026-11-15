package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.GeneratePracticeRequest
import com.kashyap.wizlyai.network.models.PracticeAnswer
import com.kashyap.wizlyai.network.models.PracticeAttempt
import com.kashyap.wizlyai.network.models.PracticeSet
import com.kashyap.wizlyai.network.models.SubmitPracticeRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Practice sets, lecture quizzes, mock exams and their attempts. */
class PracticeRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json

    suspend fun generate(
        courseId: String,
        request: GeneratePracticeRequest,
    ): Result<PracticeSet> =
        call(PracticeSet.serializer()) {
            client.post("${baseUrl()}/courses/$courseId/practice-sets/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun listSets(courseId: String): Result<List<PracticeSet>> =
        call(ListSerializer(PracticeSet.serializer())) { client.get("${baseUrl()}/practice-sets") { parameter("course_id", courseId) } }

    suspend fun getSet(setId: String): Result<PracticeSet> = call(PracticeSet.serializer()) { client.get("${baseUrl()}/practice-sets/$setId") }

    suspend fun deleteSet(setId: String): Result<Unit> =
        call(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/practice-sets/$setId") }.map { }

    suspend fun startAttempt(setId: String): Result<PracticeAttempt> =
        call(PracticeAttempt.serializer()) { client.post("${baseUrl()}/practice-sets/$setId/attempts") }

    suspend fun submit(
        attemptId: String,
        answers: List<PracticeAnswer>,
    ): Result<PracticeAttempt> =
        call(PracticeAttempt.serializer()) {
            client.post("${baseUrl()}/practice-attempts/$attemptId/submit") {
                contentType(ContentType.Application.Json)
                setBody(SubmitPracticeRequest(answers))
                timeout {
                    requestTimeoutMillis = GRADING_TIMEOUT_MS
                    socketTimeoutMillis = GRADING_TIMEOUT_MS
                }
            }
        }

    suspend fun regrade(attemptId: String): Result<PracticeAttempt> =
        call(PracticeAttempt.serializer()) {
            client.post("${baseUrl()}/practice-attempts/$attemptId/regrade") {
                timeout {
                    requestTimeoutMillis = GRADING_TIMEOUT_MS
                    socketTimeoutMillis = GRADING_TIMEOUT_MS
                }
            }
        }

    suspend fun getAttempt(attemptId: String): Result<PracticeAttempt> =
        call(PracticeAttempt.serializer()) { client.get("${baseUrl()}/practice-attempts/$attemptId") }

    suspend fun listAttempts(courseId: String): Result<List<PracticeAttempt>> =
        call(ListSerializer(PracticeAttempt.serializer())) { client.get("${baseUrl()}/practice-attempts") { parameter("course_id", courseId) } }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> call(
        serializer: KSerializer<T>,
        allowEmptyData: Boolean = false,
        request: suspend () -> HttpResponse,
    ): Result<T> =
        try {
            val response = request()
            val envelope =
                try {
                    json.decodeFromString(Envelope.serializer(serializer), response.bodyAsText())
                } catch (e: Exception) {
                    return Result.failure(CourseApiException("Request failed (${response.status.value})", response.status.value))
                }
            val data = envelope.data ?: if (allowEmptyData && envelope.success) JsonObject(emptyMap()) as T else null
            if (!response.status.isSuccess() || !envelope.success || data == null) {
                Result.failure(CourseApiException(envelope.error ?: "Request failed (${response.status.value})", response.status.value))
            } else {
                Result.success(data)
            }
        } catch (e: Exception) {
            println("⚠️ PracticeRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }

    companion object {
        const val GRADING_TIMEOUT_MS = 120_000L
    }
}
