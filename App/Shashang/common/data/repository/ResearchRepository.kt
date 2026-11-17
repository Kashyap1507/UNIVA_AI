package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.CompareRequest
import com.kashyap.wizlyai.network.models.CompareResult
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.ExportResult
import com.kashyap.wizlyai.network.models.MaterialSummaryResponse
import com.kashyap.wizlyai.network.models.Milestone
import com.kashyap.wizlyai.network.models.MilestoneInput
import com.kashyap.wizlyai.network.models.NoteDownload
import com.kashyap.wizlyai.network.models.Paper
import com.kashyap.wizlyai.network.models.PaperInput
import com.kashyap.wizlyai.network.models.ProjectInput
import com.kashyap.wizlyai.network.models.ResearchProject
import com.kashyap.wizlyai.network.models.SuggestMilestonesRequest
import com.kashyap.wizlyai.network.models.WritingFeedbackRequest
import com.kashyap.wizlyai.network.models.WritingFeedbackResult
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

/** Research projects, papers, comparison, export, writing feedback, milestones and lecture summaries. */
class ResearchRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json

    suspend fun projects(): Result<List<ResearchProject>> = call(ListSerializer(ResearchProject.serializer())) { client.get("${baseUrl()}/research/projects") }

    suspend fun project(id: String): Result<ResearchProject> = call(ResearchProject.serializer()) { client.get("${baseUrl()}/research/projects/$id") }

    suspend fun createProject(input: ProjectInput): Result<ResearchProject> = send("POST", "/research/projects", input, ResearchProject.serializer())

    suspend fun updateProject(
        id: String,
        input: ProjectInput,
    ): Result<ResearchProject> = send("PUT", "/research/projects/$id", input, ResearchProject.serializer())

    suspend fun deleteProject(id: String): Result<Unit> = empty { client.delete("${baseUrl()}/research/projects/$id") }

    suspend fun papers(projectId: String?): Result<List<Paper>> =
        call(ListSerializer(Paper.serializer())) { client.get("${baseUrl()}/research/papers") { projectId?.let { parameter("project_id", it) } } }

    suspend fun paper(id: String): Result<Paper> = call(Paper.serializer()) { client.get("${baseUrl()}/research/papers/$id") }

    suspend fun addPaper(input: PaperInput): Result<Paper> = send("POST", "/research/papers", input, Paper.serializer())

    suspend fun updatePaper(
        id: String,
        input: PaperInput,
    ): Result<Paper> = send("PUT", "/research/papers/$id", input, Paper.serializer())

    suspend fun deletePaper(id: String): Result<Unit> = empty { client.delete("${baseUrl()}/research/papers/$id") }

    suspend fun uploadPaper(
        file: PickedFile,
        projectId: String?,
    ): Result<Paper> {
        val bytes = fileReader.readBytes(file.path) ?: return Result.failure(CourseApiException("Couldn't read the selected file"))
        return call(Paper.serializer()) {
            client.submitFormWithBinaryData(
                url = "${baseUrl()}/research/papers",
                formData =
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, file.mimeType ?: "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name.replace("\"", "")}\"")
                            },
                        )
                        projectId?.let { append("project_id", it) }
                    },
            ) {
                timeout {
                    requestTimeoutMillis = SLOW_MS
                    socketTimeoutMillis = SLOW_MS
                }
            }
        }
    }

    suspend fun analyse(id: String): Result<Paper> = slowPost("/research/papers/$id/analyze", Paper.serializer())

    suspend fun paperDownload(id: String): Result<NoteDownload> = call(NoteDownload.serializer()) { client.get("${baseUrl()}/research/papers/$id/download") }

    suspend fun compare(request: CompareRequest): Result<CompareResult> =
        call(CompareResult.serializer()) {
            client.post("${baseUrl()}/research/compare") {
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout {
                    requestTimeoutMillis = SLOW_MS
                    socketTimeoutMillis = SLOW_MS
                }
            }
        }

    suspend fun export(
        format: String,
        projectId: String?,
    ): Result<ExportResult> =
        call(ExportResult.serializer()) {
            client.get("${baseUrl()}/research/export") {
                parameter("format", format)
                projectId?.let { parameter("project_id", it) }
            }
        }

    suspend fun writingFeedback(request: WritingFeedbackRequest): Result<WritingFeedbackResult> =
        call(WritingFeedbackResult.serializer()) {
            client.post("${baseUrl()}/research/writing-feedback") {
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout {
                    requestTimeoutMillis = SLOW_MS
                    socketTimeoutMillis = SLOW_MS
                }
            }
        }

    suspend fun milestones(projectId: String): Result<List<Milestone>> =
        call(ListSerializer(Milestone.serializer())) { client.get("${baseUrl()}/research/projects/$projectId/milestones") }

    suspend fun addMilestone(
        projectId: String,
        input: MilestoneInput,
    ): Result<Milestone> = send("POST", "/research/projects/$projectId/milestones", input, Milestone.serializer())

    suspend fun updateMilestone(
        id: String,
        input: MilestoneInput,
    ): Result<Milestone> = send("PUT", "/research/milestones/$id", input, Milestone.serializer())

    suspend fun deleteMilestone(id: String): Result<Unit> = empty { client.delete("${baseUrl()}/research/milestones/$id") }

    suspend fun suggestMilestones(
        projectId: String,
        request: SuggestMilestonesRequest,
    ): Result<List<Milestone>> = send("POST", "/research/projects/$projectId/milestones/suggest", request, ListSerializer(Milestone.serializer()))

    suspend fun summariseMaterial(materialId: String): Result<MaterialSummaryResponse> = slowPost("/materials/$materialId/summary", MaterialSummaryResponse.serializer())

    private suspend fun <T> slowPost(
        path: String,
        serializer: KSerializer<T>,
    ): Result<T> =
        call(serializer) {
            client.post("${baseUrl()}$path") {
                timeout {
                    requestTimeoutMillis = SLOW_MS
                    socketTimeoutMillis = SLOW_MS
                }
            }
        }

    private suspend inline fun <reified B : Any, T> send(
        method: String,
        path: String,
        body: B,
        serializer: KSerializer<T>,
    ): Result<T> =
        call(serializer) {
            if (method == "PUT") {
                client.put("${baseUrl()}$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } else {
                client.post("${baseUrl()}$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }

    private suspend fun empty(request: suspend () -> HttpResponse): Result<Unit> = call(JsonObject.serializer(), allowEmptyData = true, request).map { }

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
            println("⚠️ ResearchRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }

    companion object {
        const val SLOW_MS = 120_000L
    }
}
