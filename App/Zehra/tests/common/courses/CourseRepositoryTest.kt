package com.kashyap.wizlyai.courses

import com.kashyap.wizlyai.data.local.CacheRow
import com.kashyap.wizlyai.data.local.CourseCache
import com.kashyap.wizlyai.data.local.CourseCacheStore
import com.kashyap.wizlyai.data.local.NoOpCourseCacheStore
import com.kashyap.wizlyai.data.repository.CourseApiException
import com.kashyap.wizlyai.data.repository.CourseRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.CreateCourseRequest
import com.kashyap.wizlyai.network.models.IngestionStatus
import com.kashyap.wizlyai.session.UserSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class MemoryCache : CourseCacheStore {
    val courses = mutableMapOf<String, List<CacheRow>>()
    val materials = mutableMapOf<String, List<CacheRow>>()

    override fun saveCourses(userId: String, rows: List<CacheRow>) {
        courses[userId] = rows
    }

    override fun loadCourses(userId: String) = courses[userId].orEmpty()

    override fun saveMaterials(courseId: String, rows: List<CacheRow>) {
        materials[courseId] = rows
    }

    override fun loadMaterials(courseId: String) = materials[courseId].orEmpty()

    override fun deleteCourse(courseId: String) {
        materials.remove(courseId)
        courses.replaceAll { _, rows -> rows.filterNot { it.id == courseId } }
    }

    override fun clear() {
        courses.clear()
        materials.clear()
    }
}

class CourseRepositoryTest {
    private val cache = MemoryCache()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @BeforeTest
    fun setUp() {
        CourseCache.store = cache
        UserSessionManager.instance.startSession(AuthUser(id = "u1", email = "s@uni.edu", authProvider = "email"), token = "t")
    }

    @AfterTest
    fun tearDown() {
        CourseCache.store = NoOpCourseCacheStore
        UserSessionManager.instance.clearSession()
    }

    private fun repo(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        CourseRepository(
            client = HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(JsonConfig.json) } },
            baseUrl = { "http://test/api" },
        )

    @Test
    fun getCoursesUnwrapsEnvelopeAndCaches() =
        runTest {
            val repository =
                repo {
                    respond(
                        """{"success": true, "data": [{"id": "c1", "name": "Machine Learning", "code": "COMP-8740", "exam_dates": ["2026-12-10"], "unknown": 1}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            val (courses, fromCache) = repository.getCourses().getOrThrow()
            assertFalse(fromCache)
            assertEquals("COMP-8740 · Machine Learning", courses.single().displayTitle)
            assertEquals(listOf("c1"), cache.loadCourses("u1").map { it.id })
        }

    @Test
    fun getCoursesFallsBackToCacheWhenOffline() =
        runTest {
            repo { respond("""{"success": true, "data": [{"id": "c1", "name": "Cached course"}]}""", HttpStatusCode.OK, jsonHeaders) }
                .getCourses()
            val (courses, fromCache) = repo { throw IOException("no network") }.getCourses().getOrThrow()
            assertTrue(fromCache)
            assertEquals("Cached course", courses.single().name)
        }

    @Test
    fun offlineWithoutCacheFailsWithFriendlyMessage() =
        runTest {
            val error = repo { throw IOException("no network") }.getCourses().exceptionOrNull()
            assertIs<CourseApiException>(error)
            assertTrue(error.message!!.contains("connection"))
        }

    @Test
    fun serverErrorMessageIsSurfaced() =
        runTest {
            val error =
                repo { respond("""{"success": false, "error": "Course name is required"}""", HttpStatusCode.BadRequest, jsonHeaders) }
                    .createCourse(CreateCourseRequest(name = ""))
                    .exceptionOrNull()
            assertIs<CourseApiException>(error)
            assertEquals("Course name is required", error.message)
            assertEquals(400, error.status)
        }

    @Test
    fun createCourseOmitsNullFields() =
        runTest {
            var body = ""
            repo { request ->
                body = (request.body as io.ktor.http.content.TextContent).text
                respond("""{"success": true, "data": {"id": "c9", "name": "Stats"}}""", HttpStatusCode.Created, jsonHeaders)
            }.createCourse(CreateCourseRequest(name = "Stats", color = "#3B82F6")).getOrThrow()
            assertTrue(body.contains("\"name\""))
            assertFalse(body.contains("instructor"))
        }

    @Test
    fun deleteCourseRemovesCache() =
        runTest {
            cache.saveCourses("u1", listOf(CacheRow("c1", "{}")))
            cache.saveMaterials("c1", listOf(CacheRow("m1", "{}")))
            repo { request ->
                assertEquals(HttpMethod.Delete, request.method)
                respond("""{"success": true}""", HttpStatusCode.OK, jsonHeaders)
            }.deleteCourse("c1").getOrThrow()
            assertTrue(cache.loadCourses("u1").isEmpty() && cache.loadMaterials("c1").isEmpty())
        }

    @Test
    fun materialStatusMapping() {
        val json = JsonConfig.json
        val processing = json.decodeFromString(CourseMaterial.serializer(), """{"id": "m", "course_id": "c", "title": "L1", "ingestion_status": "PROCESSING"}""")
        val weird = processing.copy(ingestionStatus = "SOMETHING_NEW")
        assertEquals(IngestionStatus.PROCESSING, processing.status)
        assertTrue(processing.isInProgress)
        assertEquals(IngestionStatus.PENDING, weird.status)
    }
}
