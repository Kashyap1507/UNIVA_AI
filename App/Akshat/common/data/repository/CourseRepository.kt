package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.local.CacheRow
import com.kashyap.wizlyai.data.local.CourseCache
import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.Course
import com.kashyap.wizlyai.network.models.CourseConcept
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.CourseModule
import com.kashyap.wizlyai.network.models.CourseSearchResult
import com.kashyap.wizlyai.network.models.CreateCourseRequest
import com.kashyap.wizlyai.network.models.Envelope
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.util.PickedFile
import com.kashyap.wizlyai.util.fileReader
import io.ktor.client.HttpClient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Error from the course API with a message safe to show to the student. */
class CourseApiException(message: String, val status: Int = 0) : Exception(message)

/**
 * Courses, materials and course structure. Course and material lists are
 * written through to [CourseCache] and served from it when offline.
 */
class CourseRepository(
    private val client: HttpClient = ApiClient.httpClient,
    private val baseUrl: () -> String = { NetworkConstants.getApiUrl() },
) {
    private val json = JsonConfig.json
    private val cache get() = CourseCache.store

    // --- courses ---------------------------------------------------------------

    /** Returns (courses, fromCache). */
    suspend fun getCourses(): Result<Pair<List<Course>, Boolean>> {
        val userId = UserSessionManager.instance.userId ?: return Result.failure(CourseApiException("Not signed in"))
        return try {
            val courses = request(ListSerializer(Course.serializer())) { client.get("${baseUrl()}/courses") }
            onCacheThread { cache.saveCourses(userId, courses.map { it.toRow(Course.serializer()) }) }
            Result.success(courses to false)
        } catch (e: CourseApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            val cached = onCacheThread { cache.loadCourses(userId) }.mapNotNull { it.decode(Course.serializer()) }
            if (cached.isNotEmpty()) Result.success(cached to true) else Result.failure(offline(e))
        }
    }

    suspend fun getCourse(courseId: String): Result<Course> =
        runRequest(Course.serializer()) { client.get("${baseUrl()}/courses/$courseId") }

    suspend fun createCourse(request: CreateCourseRequest): Result<Course> =
        runRequest(Course.serializer()) {
            client.post("${baseUrl()}/courses") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun updateCourse(courseId: String, fields: JsonObject): Result<Course> =
        runRequest(Course.serializer()) {
            client.put("${baseUrl()}/courses/$courseId") {
                contentType(ContentType.Application.Json)
                setBody(fields)
            }
        }

    suspend fun deleteCourse(courseId: String): Result<Unit> =
        runRequest(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/courses/$courseId") }
            .map { onCacheThread { cache.deleteCourse(courseId) } }

    // --- structure --------------------------------------------------------------

    suspend fun generateStructure(courseId: String): Result<Unit> =
        runRequest(JsonObject.serializer()) { client.post("${baseUrl()}/courses/$courseId/structure/generate") }.map { }

    suspend fun getModules(courseId: String): Result<List<CourseModule>> =
        runRequest(ListSerializer(CourseModule.serializer())) { client.get("${baseUrl()}/courses/$courseId/modules") }

    suspend fun getConcepts(courseId: String): Result<List<CourseConcept>> =
        runRequest(ListSerializer(CourseConcept.serializer())) { client.get("${baseUrl()}/courses/$courseId/concepts") }

    suspend fun search(courseId: String, query: String, k: Int = 8): Result<List<CourseSearchResult>> =
        runRequest(ListSerializer(CourseSearchResult.serializer())) {
            client.get("${baseUrl()}/courses/$courseId/search") {
                parameter("q", query)
                parameter("k", k)
            }
        }

    // --- materials ---------------------------------------------------------------

    /** Returns (materials, fromCache). */
    suspend fun getMaterials(courseId: String): Result<Pair<List<CourseMaterial>, Boolean>> =
        try {
            val materials = request(ListSerializer(CourseMaterial.serializer())) { client.get("${baseUrl()}/courses/$courseId/materials") }
            onCacheThread { cache.saveMaterials(courseId, materials.map { it.toRow(CourseMaterial.serializer()) }) }
            Result.success(materials to false)
        } catch (e: CourseApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            val cached = onCacheThread { cache.loadMaterials(courseId) }.mapNotNull { it.decode(CourseMaterial.serializer()) }
            if (cached.isNotEmpty()) Result.success(cached to true) else Result.failure(offline(e))
        }

    suspend fun uploadMaterial(courseId: String, file: PickedFile, kind: String? = null): Result<CourseMaterial> {
        val bytes = fileReader.readBytes(file.path) ?: return Result.failure(CourseApiException("Couldn't read the selected file"))
        if (bytes.size > MAX_UPLOAD_BYTES) {
            return Result.failure(CourseApiException("Files must be 50 MB or smaller"))
        }
        return runRequest(CourseMaterial.serializer()) {
            client.submitFormWithBinaryData(
                url = "${baseUrl()}/courses/$courseId/materials",
                formData =
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, file.mimeType ?: "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name.replace("\"", "")}\"")
                            },
                        )
                        if (kind != null) append("kind", kind)
                    },
            )
        }
    }

    suspend fun deleteMaterial(materialId: String): Result<Unit> =
        runRequest(JsonObject.serializer(), allowEmptyData = true) { client.delete("${baseUrl()}/materials/$materialId") }.map { }

    suspend fun reingestMaterial(materialId: String): Result<CourseMaterial> =
        runRequest(CourseMaterial.serializer()) { client.post("${baseUrl()}/materials/$materialId/reingest") }

    // --- plumbing ----------------------------------------------------------------

    private suspend fun <T> runRequest(
        serializer: KSerializer<T>,
        allowEmptyData: Boolean = false,
        call: suspend () -> HttpResponse,
    ): Result<T> =
        try {
            Result.success(request(serializer, allowEmptyData, call))
        } catch (e: CourseApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(offline(e))
        }

    /** Performs the call and unwraps the `{success, data, error}` envelope. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> request(
        serializer: KSerializer<T>,
        allowEmptyData: Boolean = false,
        call: suspend () -> HttpResponse,
    ): T {
        val response = call()
        val text = response.bodyAsText()
        val envelope =
            try {
                json.decodeFromString(Envelope.serializer(serializer), text)
            } catch (e: Exception) {
                throw CourseApiException(
                    if (response.status.isSuccess()) "Unexpected response from server" else "Request failed (${response.status.value})",
                    response.status.value,
                )
            }
        if (!response.status.isSuccess() || !envelope.success) {
            throw CourseApiException(envelope.error ?: "Request failed (${response.status.value})", response.status.value)
        }
        return envelope.data ?: if (allowEmptyData) JsonObject(emptyMap()) as T else throw CourseApiException("Empty response from server")
    }

    private fun offline(e: Exception): CourseApiException {
        println("⚠️ CourseRepository: network error: ${e.message}")
        return CourseApiException("Can't reach the server. Check your connection and try again.")
    }

    private fun <T> T.toRow(serializer: KSerializer<T>): CacheRow {
        val id =
            when (this) {
                is Course -> id
                is CourseMaterial -> id
                else -> error("Unsupported cache type")
            }
        val updatedAt =
            when (this) {
                is Course -> updatedAt
                is CourseMaterial -> updatedAt
                else -> null
            }
        return CacheRow(id = id, json = json.encodeToString(serializer, this), updatedAt = updatedAt)
    }

    private fun <T> CacheRow.decode(serializer: KSerializer<T>): T? =
        try {
            this@CourseRepository.json.decodeFromString(serializer, this.json)
        } catch (e: Exception) {
            null
        }

    private suspend fun <T> onCacheThread(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        const val MAX_UPLOAD_BYTES = 50 * 1024 * 1024
    }
}
