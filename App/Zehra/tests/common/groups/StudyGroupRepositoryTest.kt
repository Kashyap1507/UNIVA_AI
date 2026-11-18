package com.kashyap.wizlyai.groups

import com.kashyap.wizlyai.data.repository.StudyGroupRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.ReportReason
import com.kashyap.wizlyai.network.models.ReportRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyGroupRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = StudyGroupRepository(
        client =
            HttpClient(
                MockEngine { req ->
                    requests += req
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                install(ContentNegotiation) { json(JsonConfig.json) }
                install(HttpTimeout)
            },
        baseUrl = { "http://test/api" },
    )

    @Test
    fun discoveredGroupsHaveNoInviteCodeOrRole() =
        runTest {
            val found =
                repo("""{"success": true, "data": [{"id": "g1", "name": "OS crew", "course_code": "COMP-3300", "discoverable": true, "member_count": 4}]}""")
                    .discover("COMP-3300").getOrThrow()
            assertNull(found.single().inviteCode)
            assertEquals(false, found.single().isOwner)
            assertEquals("COMP-3300", requests.single().url.parameters["course_code"])
        }

    @Test
    fun groupDetailAndNotesDecode() =
        runTest {
            val g =
                repo(
                    """{"success": true, "data": {"id": "g1", "name": "OS crew", "member_count": 2, "role": "OWNER", "invite_code": "ABCD2345",
                    "members": [{"user_id": "u1", "display_name": "Priya S.", "role": "OWNER", "is_me": true}, {"user_id": "u2", "display_name": "Alex", "role": "MEMBER"}]}}""",
                ).group("g1").getOrThrow()
            assertTrue(g.isOwner)
            assertEquals("ABCD2345", g.inviteCode)
            assertEquals(listOf("Priya S.", "Alex"), g.members!!.map { it.displayName })

            val notes =
                repo(
                    """{"success": true, "data": [{"id": "n1", "group_id": "g1", "kind": "MATERIAL", "title": "Week 3 notes", "author_name": "Alex",
                    "is_mine": false, "can_remove": true, "material_title": "Week 3 notes"}]}""",
                ).notes("g1").getOrThrow()
            assertEquals("MATERIAL", notes.single().kind)
            assertTrue(notes.single().canRemove)
        }

    @Test
    fun reportSendsReasonAndSurfacesServerErrors() =
        runTest {
            val r = repo("""{"success": true, "data": {"id": "r1", "status": "OPEN", "hidden": true}}""")
            val result = r.report(ReportRequest("g1", "NOTE", "n1", ReportReason.ACADEMIC_INTEGRITY.apiName)).getOrThrow()
            assertTrue(result.hidden)
            val sent = JsonConfig.json.parseToJsonElement((requests.last().body as TextContent).text).jsonObject
            assertEquals("ACADEMIC_INTEGRITY", sent["reason"]!!.jsonPrimitive.content)

            val err = repo("""{"success": false, "error": "You've already reported this."}""", HttpStatusCode.Conflict).report(ReportRequest("g1", "NOTE", "n1", "SPAM"))
            assertEquals("You've already reported this.", err.exceptionOrNull()?.message)
        }
}
