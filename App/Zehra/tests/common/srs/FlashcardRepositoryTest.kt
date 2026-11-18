package com.kashyap.wizlyai.srs

import com.kashyap.wizlyai.data.local.InMemoryJsonStore
import com.kashyap.wizlyai.data.repository.FlashcardRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.Flashcard
import com.kashyap.wizlyai.network.models.FlashcardSrs
import com.kashyap.wizlyai.ui.util.MathMarkdown
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class FlashcardRepositoryTest {
    private val store = InMemoryJsonStore()
    private val clock = FixedClock(Instant.parse("2026-09-27T10:00:00Z"))
    private var online = true
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()
    private var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { ok("[]") }

    private fun MockRequestHandleScope.ok(
        data: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        if (status.value < 400) "{\"success\": true, \"data\": $data}" else "{\"success\": false, \"error\": \"$data\"}",
        status,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val repo =
        FlashcardRepository(
            client =
                HttpClient(
                    MockEngine { request ->
                        if (!online) throw IllegalStateException("offline")
                        requests += request to ((request.body as? TextContent)?.text ?: "")
                        handler(request)
                    },
                ) {
                    install(ContentNegotiation) { json(JsonConfig.json) }
                    install(HttpTimeout)
                },
            baseUrl = { "http://test/api" },
            store = { store },
            userId = { "u1" },
            clock = clock,
        )

    private fun card(
        id: String,
        due: String = "2026-09-27T09:00:00+00:00",
    ) = Flashcard(id = id, courseId = "c1", front = "Q $id", back = "A $id", srs = FlashcardSrs(dueAt = due))

    private fun cardJson(
        id: String,
        due: String = "2026-09-27T09:00:00.123456+00:00",
        reps: Int = 0,
        interval: Int = 0,
    ) = "{\"id\": \"$id\", \"course_id\": \"c1\", \"front\": \"Q $id\", \"back\": \"A $id\", " +
        "\"srs\": {\"ease\": 2.5, \"interval_days\": $interval, \"reps\": $reps, \"lapses\": 0, \"due_at\": \"$due\"}}"

    @Test
    fun parsesServerTimestamps() {
        val srs = FlashcardSrs(dueAt = "2026-09-27T09:00:00.123456+00:00")
        assertEquals(Instant.parse("2026-09-27T09:00:00.123456Z"), srs.dueInstant)
    }

    @Test
    fun offlineReviewIsScheduledLocallyAndReplayedInOrder() =
        runTest {
            // Load two due cards while online so they're cached
            handler = { ok("[${cardJson("a")}, ${cardJson("b")}]") }
            val (due, fromCache) = repo.dueCards("c1").getOrThrow()
            assertEquals(listOf("a", "b"), due.map { it.id })
            assertTrue(!fromCache)

            // Go offline: review both; the schedule is computed on the device
            online = false
            val a = repo.review(due[0], ReviewGrade.GOOD.grade)
            clock.now += 1.hours
            repo.review(due[1], ReviewGrade.AGAIN.grade)
            assertEquals(1, a.srs.intervalDays)
            assertEquals(Instant.parse("2026-09-28T10:00:00Z"), a.srs.dueInstant)
            assertEquals(2, repo.pendingCount())

            // Offline due list comes from the cache and no longer includes them
            val (offlineDue, cached) = repo.dueCards("c1").getOrThrow()
            assertTrue(cached)
            assertEquals(emptyList(), offlineDue)

            // Back online: queued reviews go out in order with their original times
            online = true
            requests.clear()
            handler = { req -> ok(cardJson(req.url.encodedPath.split("/")[3], due = "2026-09-28T10:00:00+00:00", reps = 1, interval = 1)) }
            assertEquals(2, repo.syncPending())
            val sent = requests.filter { it.first.method == HttpMethod.Post }
            assertEquals(listOf("/api/flashcards/a/review", "/api/flashcards/b/review"), sent.map { it.first.url.encodedPath })
            val first = JsonConfig.json.parseToJsonElement(sent[0].second).jsonObject
            assertEquals("4", first["grade"]!!.jsonPrimitive.content)
            assertEquals("2026-09-27T10:00:00Z", first["reviewed_at"]!!.jsonPrimitive.content)
            val ids = sent.map { JsonConfig.json.parseToJsonElement(it.second).jsonObject["client_review_id"]!!.jsonPrimitive.content }
            assertTrue(ids.all { Regex("^[A-Za-z0-9_-]{8,64}$").matches(it) } && ids.toSet().size == 2)
            assertEquals(0, repo.pendingCount())
        }

    @Test
    fun rejectedReviewIsDroppedButNetworkFailureKeepsIt() =
        runTest {
            online = false
            repo.review(card("gone"), ReviewGrade.GOOD.grade)
            repo.review(card("kept"), ReviewGrade.EASY.grade)
            assertEquals(2, repo.pendingCount())

            // Card deleted on another device → 404 → dropped; then the server goes down
            online = true
            var calls = 0
            handler = { calls++; if (calls == 1) ok("Flashcard not found", HttpStatusCode.NotFound) else ok("Something went wrong", HttpStatusCode.InternalServerError) }
            assertEquals(0, repo.syncPending())
            assertEquals(1, repo.pendingCount())
        }

    @Test
    fun cachedDueCardsAreServedOffline() =
        runTest {
            handler = { ok("[${cardJson("x")}, ${cardJson("y", due = "2026-10-05T00:00:00+00:00")}]") }
            repo.courseCards("c1").getOrThrow()
            online = false
            val (due, fromCache) = repo.dueCards(null).getOrThrow()
            assertTrue(fromCache)
            assertEquals(listOf("x"), due.map { it.id })
            clock.now += 10.days
            assertEquals(2, repo.summary().getOrThrow().dueTotal)
        }

    @Test
    fun plainPreviewConvertsMaths() {
        assertEquals("What is ∫₀¹ x² dx?", MathMarkdown.plain("What is $\\int_0^1 x^2\\,dx$?"))
        assertEquals("Bold and list", MathMarkdown.plain("**Bold**\n- and list"))
    }
}
