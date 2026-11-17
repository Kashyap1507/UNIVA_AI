package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.CreateGroupRequest
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.network.models.JoinGroupRequest
import com.kashyap.wizlyai.network.models.NoteDownload
import com.kashyap.wizlyai.network.models.NoteRequest
import com.kashyap.wizlyai.network.models.ReportRequest
import com.kashyap.wizlyai.network.models.ReportResult
import com.kashyap.wizlyai.network.models.SharedNote
import com.kashyap.wizlyai.network.models.StudyGroup
import com.kashyap.wizlyai.network.models.UpdateGroupRequest
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

/** Study groups, shared notes and reports. */
class StudyGroupRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json
    private val groupList = ListSerializer(StudyGroup.serializer())

    suspend fun myGroups(): Result<List<StudyGroup>> = call(groupList) { client.get("${baseUrl()}/groups") }

    suspend fun create(request: CreateGroupRequest): Result<StudyGroup> = post("/groups", request, StudyGroup.serializer())

    suspend fun joinByCode(code: String): Result<StudyGroup> = post("/groups/join", JoinGroupRequest(code), StudyGroup.serializer())

    suspend fun discover(courseCode: String): Result<List<StudyGroup>> = call(groupList) { client.get("${baseUrl()}/groups/discover") { parameter("course_code", courseCode) } }

    suspend fun joinDiscoverable(groupId: String): Result<StudyGroup> = call(StudyGroup.serializer()) { client.post("${baseUrl()}/groups/$groupId/join") }

    suspend fun group(groupId: String): Result<StudyGroup> = call(StudyGroup.serializer()) { client.get("${baseUrl()}/groups/$groupId") }

    suspend fun update(
        groupId: String,
        request: UpdateGroupRequest,
    ): Result<StudyGroup> =
        call(StudyGroup.serializer()) {
            client.put("${baseUrl()}/groups/$groupId") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun resetCode(groupId: String): Result<StudyGroup> = call(StudyGroup.serializer()) { client.post("${baseUrl()}/groups/$groupId/invite-code") }

    suspend fun leave(groupId: String): Result<Unit> = empty { client.post("${baseUrl()}/groups/$groupId/leave") }

    suspend fun removeMember(
        groupId: String,
        userId: String,
    ): Result<Unit> = empty { client.delete("${baseUrl()}/groups/$groupId/members/$userId") }

    suspend fun notes(groupId: String): Result<List<SharedNote>> = call(ListSerializer(SharedNote.serializer())) { client.get("${baseUrl()}/groups/$groupId/notes") }

    suspend fun postNote(
        groupId: String,
        request: NoteRequest,
    ): Result<SharedNote> = post("/groups/$groupId/notes", request, SharedNote.serializer())

    suspend fun deleteNote(
        groupId: String,
        noteId: String,
    ): Result<Unit> = empty { client.delete("${baseUrl()}/groups/$groupId/notes/$noteId") }

    suspend fun download(
        groupId: String,
        noteId: String,
    ): Result<NoteDownload> = call(NoteDownload.serializer()) { client.get("${baseUrl()}/groups/$groupId/notes/$noteId/download") }

    suspend fun report(request: ReportRequest): Result<ReportResult> = post("/reports", request, ReportResult.serializer())

    private suspend inline fun <reified B : Any, T> post(
        path: String,
        body: B,
        serializer: KSerializer<T>,
    ): Result<T> =
        call(serializer) {
            client.post("${baseUrl()}$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
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
            println("⚠️ StudyGroupRepository: ${e.message}")
            Result.failure(CourseApiException("Can't reach the server. Check your connection and try again."))
        }
}
