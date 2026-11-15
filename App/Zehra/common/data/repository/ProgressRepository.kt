package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.CourseProgress
import com.kashyap.wizlyai.network.models.CourseProgressDetail
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.GradeEntry
import com.kashyap.wizlyai.network.models.GradeInput
import com.kashyap.wizlyai.network.models.GradesOverview
import com.kashyap.wizlyai.network.models.TodayData
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Today, course progress and the grade tracker. `date` is the device's local day (YYYY-MM-DD). */
class ProgressRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json

    suspend fun today(date: String): Result<TodayData> = call(TodayData.serializer()) { client.get("${baseUrl()}/today") { parameter("date", date) } }

    suspend fun courses(date: String): Result<List<CourseProgress>> =
        call(ListSerializer(CourseProgress.serializer())) { client.get("${baseUrl()}/progress/courses") { parameter("date", date) } }

    suspend fun course(
        courseId: String,
        date: String,
    ): Result<CourseProgressDetail> = call(CourseProgressDetail.serializer()) { client.get("${baseUrl()}/progress/courses/$courseId") { parameter("date", date) } }

    suspend fun grades(courseId: String? = null): Result<GradesOverview> =
        call(GradesOverview.serializer()) { client.get("${baseUrl()}/grades-tracker") { courseId?.let { parameter("course_id", it) } } }

    suspend fun addGrade(input: GradeInput): Result<GradeEntry> =
        call(GradeEntry.serializer()) {
            client.post("${baseUrl()}/grades-tracker") {
                contentType(ContentType.Application.Json)
                setBody(input)
            }
        }

    suspend fun updateGrade(
        id: String,
        input: GradeInput,
    ): Result<GradeEntry> =
        call(GradeEntry.serializer()) {
            client.put("${baseUrl()}/grades-tracker/$id") {
                contentType(ContentType.Application.Json)
                setBody(input)
            }
        }

    suspend fun deleteGrade(id: String): Result<Unit> = call(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/grades-tracker/$id") }.map { }

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
            println("⚠️ ProgressRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }
}
