package com.kashyap.wizlyai.work

import com.kashyap.wizlyai.data.repository.WorkRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.WorkStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkRepositoryTest {
    private fun repo(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        WorkRepository(
            client =
                HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "text/event-stream")) }) {
                    install(ContentNegotiation) { json(JsonConfig.json) }
                    install(HttpTimeout)
                },
            baseUrl = { "http://test/api" },
        )

    @Test
    fun stepsThenDoneWithHintsOnlyFeedback() =
        runTest {
            val body =
                "event: step\ndata: {\"step\": \"extract\"}\n\n" +
                    "event: step\ndata: {\"step\": \"evaluate\"}\n\n" +
                    "event: done\ndata: {\"id\": \"s1\", \"status\": \"READY\", \"mode\": \"HINTS\", \"feedback\": " +
                    "{\"mode\": \"HINTS\", \"summary\": \"One error\", \"items\": [{\"problem\": \"1\", \"correct\": false, \"hint\": \"Check the product rule\"}]}}\n\n"
            val events = repo(body).stream("s1").toList()
            assertEquals(listOf("extract", "evaluate"), events.filterIsInstance<WorkStreamEvent.Step>().map { it.step })
            val done = assertIs<WorkStreamEvent.Done>(events.last())
            val feedback = done.submission.feedback!!
            assertFalse(feedback.isPractice)
            assertNull(feedback.items.single().solution)
        }

    @Test
    fun errorEventEndsStream() =
        runTest {
            val events = repo("event: error\ndata: {\"message\": \"Scan again\"}\n\n").stream("s1").toList()
            assertEquals(listOf<WorkStreamEvent>(WorkStreamEvent.Failed("Scan again")), events)
        }

    @Test
    fun droppedConnectionIsReported() =
        runTest {
            val events = repo("event: step\ndata: {\"step\": \"extract\"}\n\n").stream("s1").toList()
            assertTrue(events.last() is WorkStreamEvent.Failed)
        }
}
