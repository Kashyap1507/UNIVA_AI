package com.kashyap.wizlyai.research

import com.kashyap.wizlyai.data.repository.CourseApiException
import com.kashyap.wizlyai.data.repository.ResearchRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.CompareRequest
import com.kashyap.wizlyai.network.models.CourseMaterial
import com.kashyap.wizlyai.network.models.WritingFeedbackRequest
import com.kashyap.wizlyai.ui.viewmodel.ProjectViewModel
import com.kashyap.wizlyai.ui.viewmodel.parseAuthors
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResearchRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = ResearchRepository(
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
    fun paperWithSummaryDecodes() =
        runTest {
            val p =
                repo(
                    """{"success": true, "data": {"id": "p1", "title": "Attention Is All You Need", "authors": ["Vaswani, Ashish", "Shazeer, Noam"],
                    "year": 2017, "type": "CONFERENCE", "status": "ANALYSED", "has_text": true, "citation": "Vaswani et al., 2017",
                    "summary": {"tldr": "Transformers.", "findings": ["BLEU 28.4"], "keywords": ["attention"]}}}""",
                ).paper("p1").getOrThrow()
            assertTrue(p.isAnalysed)
            assertEquals("Vaswani et al., 2017", p.citation)
            assertEquals(listOf("BLEU 28.4"), p.summary!!.findings)
            assertEquals("", p.summary!!.methods)
        }

    @Test
    fun paperWithoutTextIsNotAnalysed() =
        runTest {
            val p = repo("""{"success": true, "data": {"id": "p2", "status": "NO_TEXT", "file_name": "scan.pdf"}}""").paper("p2").getOrThrow()
            assertFalse(p.isAnalysed)
            assertFalse(p.hasText)
            assertNull(p.title)
        }

    @Test
    fun compareSendsIdsAndDecodesTableOnlyFallback() =
        runTest {
            val r =
                repo(
                    """{"success": true, "data": {"rows": [{"number": 1, "citation": "Smith, 2020", "findings": "x"}, {"number": 2, "citation": "Lee & Kim, 2021"}],
                    "synthesis": null, "note": "Synthesis unavailable"}}""",
                ).compare(CompareRequest(listOf("a", "b"), "efficiency")).getOrThrow()
            assertEquals(2, r.rows.size)
            assertNull(r.synthesis)
            assertEquals("Synthesis unavailable", r.note)
            val sent = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject
            assertEquals(listOf("a", "b"), sent["paper_ids"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("efficiency", sent["focus"]!!.jsonPrimitive.content)
        }

    @Test
    fun exportPassesFormatAndProject() =
        runTest {
            val e = repo("""{"success": true, "data": {"format": "bibtex", "count": 1, "text": "@article{smith2020,"}}""").export("bibtex", "proj1").getOrThrow()
            assertEquals(1, e.count)
            assertEquals("bibtex", requests.single().url.parameters["format"])
            assertEquals("proj1", requests.single().url.parameters["project_id"])
        }

    @Test
    fun writingFeedbackErrorSurfacesServerMessage() =
        runTest {
            val r =
                repo("""{"success": false, "error": "The AI service is not available right now"}""", HttpStatusCode.BadGateway)
                    .writingFeedback(WritingFeedbackRequest("x".repeat(300), "ESSAY"))
            val e = r.exceptionOrNull() as CourseApiException
            assertEquals("The AI service is not available right now", e.message)
            assertEquals(502, e.status)
        }

    @Test
    fun milestonesAndMaterialSummaryDecode() =
        runTest {
            val ms =
                repo("""{"success": true, "data": [{"id": "m1", "title": "Literature review", "due_at": "2026-11-01", "status": "IN_PROGRESS", "template": true}]}""")
                    .milestones("proj1").getOrThrow()
            assertEquals("IN_PROGRESS", ms.single().status)
            assertTrue(ms.single().template)

            val s =
                repo(
                    """{"success": true, "data": {"material_id": "mat1", "title": "Lecture 3", "summary": {"overview": "Paging.",
                    "key_points": ["TLB"], "terms": [{"term": "TLB", "definition": "cache"}], "exam_questions": ["Why?"]}}}""",
                ).summariseMaterial("mat1").getOrThrow()
            assertEquals("TLB", s.summary.terms.single().term)
            assertTrue(requests.last().url.encodedPath.endsWith("/materials/mat1/summary"))
        }

    @Test
    fun materialKeepsSavedSummary() {
        val m =
            JsonConfig.json.decodeFromString(
                CourseMaterial.serializer(),
                """{"id": "m", "course_id": "c", "title": "L1", "summary": {"overview": "Intro"}}""",
            )
        assertEquals("Intro", m.summary!!.overview)
        assertNull(JsonConfig.json.decodeFromString(CourseMaterial.serializer(), """{"id": "m", "course_id": "c", "title": "L1"}""").summary)
    }

    @Test
    fun authorsSplitOnSemicolonsAndLines() {
        assertEquals(listOf("Ada Lovelace", "Turing, Alan", "Grace Hopper"), parseAuthors("Ada  Lovelace; Turing, Alan\n\n Grace Hopper ;"))
        assertEquals(emptyList(), parseAuthors("  ; \n"))
    }

    @Test
    fun milestoneStatusCycles() {
        assertEquals("IN_PROGRESS", ProjectViewModel.nextStatus("TODO"))
        assertEquals("DONE", ProjectViewModel.nextStatus("IN_PROGRESS"))
        assertEquals("TODO", ProjectViewModel.nextStatus("DONE"))
    }
}
