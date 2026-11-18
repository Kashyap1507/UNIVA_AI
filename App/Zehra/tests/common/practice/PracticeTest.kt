package com.kashyap.wizlyai.practice

import com.kashyap.wizlyai.data.repository.PracticeRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.ExamQuestion
import com.kashyap.wizlyai.network.models.PracticeKind
import com.kashyap.wizlyai.ui.viewmodel.ExamSession
import com.kashyap.wizlyai.ui.viewmodel.ExamViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ExamTimerTest {
    private val t0 = Instant.parse("2026-09-27T10:00:00Z")

    @Test
    fun countdownRunsFromLocalStartWithServerLimit() {
        // Server says 30 min; the device clock is 2 minutes behind the server
        val deadline = ExamTimer.localDeadline(t0 + 2.minutes, t0 + 32.minutes, receivedAt = t0)!!
        assertEquals(t0 + 30.minutes, deadline)
        assertEquals(1800L, ExamTimer.remainingSeconds(deadline, t0))
        assertEquals(0L, ExamTimer.remainingSeconds(deadline, t0 + 31.minutes))
        assertFalse(ExamTimer.isUp(deadline, t0 + 29.minutes))
        assertTrue(ExamTimer.isUp(deadline, t0 + 30.minutes))
    }

    @Test
    fun untimedAttemptsHaveNoDeadline() {
        assertNull(ExamTimer.localDeadline(t0, null, t0))
        assertNull(ExamTimer.localDeadline(t0, t0, t0))
        assertNull(ExamTimer.remainingSeconds(null, t0))
        assertFalse(ExamTimer.isUp(null, t0 + 999.minutes))
    }

    @Test
    fun formatAndWarning() {
        assertEquals("04:32", ExamTimer.format(272))
        assertEquals("1:02:05", ExamTimer.format(3725))
        assertEquals("00:00", ExamTimer.format(-5))
        assertTrue(ExamTimer.isWarning(300))
        assertFalse(ExamTimer.isWarning(301))
        assertFalse(ExamTimer.isWarning(null))
        assertEquals(59L, ExamTimer.remainingSeconds(t0 + 60.seconds, t0 + 500.milliseconds()))
    }

    private fun Int.milliseconds() = kotlin.time.Duration.parse("${this}ms")
}

class PracticeTest {
    private val questions =
        listOf(
            ExamQuestion(id = "q1", type = "MCQ", prompt = "Pick", options = listOf("a", "b")),
            ExamQuestion(id = "q2", type = "NUMERIC", prompt = "Compute"),
            ExamQuestion(id = "q3", type = "SHORT", prompt = "Explain"),
            ExamQuestion(id = "q4", type = "PROOF", prompt = "Prove"),
        )

    @Test
    fun answersAreSentAsIndexOrTrimmedTextAndBlanksSkipped() {
        val session =
            ExamSession(
                attemptId = "a1",
                title = "Quiz",
                kind = PracticeKind.PRACTICE,
                questions = questions,
                answers = mapOf("q1" to 1, "q2" to " 7/2 ", "q3" to "   "),
            )
        val answers = ExamViewModel.toAnswers(session)
        assertEquals(listOf("q1", "q2"), answers.map { it.questionId })
        assertEquals(JsonPrimitive(1), answers[0].response)
        assertEquals(JsonPrimitive("7/2"), answers[1].response)
        assertEquals(2, session.answeredCount)
        assertEquals(2, session.unanswered)
    }

    @Test
    fun submitDecodesReviewWithAnswersAndConcepts() =
        runTest {
            var sent: HttpRequestData? = null
            val body =
                """{"success": true, "data": {"id": "a1", "set_id": "s1", "course_id": "c1", "kind": "MOCK_EXAM", "status": "GRADED",
                "points": 6.5, "max_points": 7, "score_pct": 92.9, "ungraded": 0, "late": false,
                "per_concept": {"k1": {"concept_id": "k1", "name": "Eigenvalues", "points": 1, "max_points": 1, "correct": 1, "total": 1}},
                "review": [
                  {"id": "q1", "type": "MCQ", "prompt": "Pick", "options": ["a", "b"], "points": 1, "correct_option": 1, "response": 1,
                   "result": {"status": "GRADED", "correct": true, "points_awarded": 1, "feedback": null}},
                  {"id": "q3", "type": "SHORT", "prompt": "Explain", "points": 4, "model_answer": "Because.", "response": "Since",
                   "result": {"status": "NOT_GRADED", "correct": null, "points_awarded": 0}}
                ]}}"""
            val repo =
                PracticeRepository(
                    client =
                        HttpClient(
                            MockEngine { req ->
                                sent = req
                                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        ) {
                            install(ContentNegotiation) { json(JsonConfig.json) }
                            install(HttpTimeout)
                        },
                    baseUrl = { "http://test/api" },
                )
            val session = ExamSession("a1", "Mock", PracticeKind.MOCK_EXAM, questions, answers = mapOf("q1" to 1, "q3" to "Since"))
            val result = repo.submit("a1", ExamViewModel.toAnswers(session)).getOrThrow()
            assertEquals("/api/practice-attempts/a1/submit", sent!!.url.encodedPath)
            val payload = (sent!!.body as TextContent).text
            val sentAnswers = JsonConfig.json.parseToJsonElement(payload).jsonObject["answers"]!!.jsonArray.map { it.jsonObject["response"] }
            assertEquals(listOf(JsonPrimitive(1), JsonPrimitive("Since")), sentAnswers)
            assertEquals(92.9, result.scorePct)
            assertEquals(1f, result.perConcept.getValue("k1").fraction)
            val review = result.review!!
            assertEquals("b", review[0].responseText)
            assertTrue(review[0].result.isGraded && review[0].result.correct == true)
            assertEquals("Since", review[1].responseText)
            assertFalse(review[1].result.isGraded)
        }
}
