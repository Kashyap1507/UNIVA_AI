package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.BulkDeadlinesRequest
import com.kashyap.wizlyai.network.models.Deadline
import com.kashyap.wizlyai.network.models.DeadlineInput
import com.kashyap.wizlyai.network.models.DeadlineUpdate
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.FeedUrlRequest
import com.kashyap.wizlyai.network.models.GeneratePlanRequest
import com.kashyap.wizlyai.network.models.IcalImportResult
import com.kashyap.wizlyai.network.models.StudyPlan
import com.kashyap.wizlyai.network.models.SyllabusPreview
import com.kashyap.wizlyai.network.models.TaskDoneRequest
import com.kashyap.wizlyai.util.PickedFile
import com.kashyap.wizlyai.util.fileReader
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Deadlines, syllabus/iCal import and exam study plans. */
class DeadlineRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json

    suspend fun list(courseId: String? = null): Result<List<Deadline>> =
        call(ListSerializer(Deadline.serializer())) { client.get("${baseUrl()}/deadlines") { courseId?.let { parameter("course_id", it) } } }

    suspend fun create(input: DeadlineInput): Result<Deadline> =
        call(Deadline.serializer()) {
            client.post("${baseUrl()}/deadlines") {
                contentType(ContentType.Application.Json)
                setBody(input)
            }
        }

    suspend fun createBulk(
        source: String,
        items: List<DeadlineInput>,
    ): Result<List<Deadline>> =
        call(ListSerializer(Deadline.serializer())) {
            client.post("${baseUrl()}/deadlines/bulk") {
                contentType(ContentType.Application.Json)
                setBody(BulkDeadlinesRequest(source, items))
            }
        }

    suspend fun update(
        id: String,
        update: DeadlineUpdate,
    ): Result<Deadline> =
        call(Deadline.serializer()) {
            client.put("${baseUrl()}/deadlines/$id") {
                contentType(ContentType.Application.Json)
                setBody(update)
            }
        }

    suspend fun delete(id: String): Result<Unit> = call(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/deadlines/$id") }.map { }

    suspend fun importIcalFile(file: PickedFile): Result<IcalImportResult> {
        val bytes = fileReader.readBytes(file.path) ?: return Result.failure(CourseApiException("Couldn't read the selected file"))
        return call(IcalImportResult.serializer()) {
            client.submitFormWithBinaryData(
                url = "${baseUrl()}/deadlines/import-ical",
                formData =
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "text/calendar")
                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name.replace("\"", "")}\"")
                            },
                        )
                    },
            )
        }
    }

    suspend fun importIcalUrl(url: String): Result<IcalImportResult> =
        call(IcalImportResult.serializer()) {
            client.post("${baseUrl()}/deadlines/import-ical") {
                contentType(ContentType.Application.Json)
                setBody(FeedUrlRequest(url))
                timeout {
                    requestTimeoutMillis = SLOW_TIMEOUT_MS
                    socketTimeoutMillis = SLOW_TIMEOUT_MS
                }
            }
        }

    suspend fun extractSyllabus(courseId: String): Result<SyllabusPreview> =
        call(SyllabusPreview.serializer()) {
            client.post("${baseUrl()}/courses/$courseId/syllabus/extract") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
                timeout {
                    requestTimeoutMillis = SLOW_TIMEOUT_MS
                    socketTimeoutMillis = SLOW_TIMEOUT_MS
                }
            }
        }

    suspend fun generatePlan(request: GeneratePlanRequest): Result<StudyPlan> =
        call(StudyPlan.serializer()) {
            client.post("${baseUrl()}/study-plans/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun plans(courseId: String? = null): Result<List<StudyPlan>> =
        call(ListSerializer(StudyPlan.serializer())) { client.get("${baseUrl()}/study-plans") { courseId?.let { parameter("course_id", it) } } }

    suspend fun plan(id: String): Result<StudyPlan> = call(StudyPlan.serializer()) { client.get("${baseUrl()}/study-plans/$id") }

    suspend fun setTaskDone(
        planId: String,
        taskId: String,
        done: Boolean,
    ): Result<StudyPlan> =
        call(StudyPlan.serializer()) {
            client.put("${baseUrl()}/study-plans/$planId/tasks/$taskId") {
                contentType(ContentType.Application.Json)
                setBody(TaskDoneRequest(done))
            }
        }

    suspend fun deletePlan(id: String): Result<Unit> = call(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/study-plans/$id") }.map { }

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
            println("⚠️ DeadlineRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }

    companion object {
        const val SLOW_TIMEOUT_MS = 90_000L
    }
}
