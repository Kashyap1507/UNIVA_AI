package com.kashyap.wizlyai.tutor

import com.kashyap.wizlyai.data.repository.TutorRepository
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.SseEvent
import com.kashyap.wizlyai.network.SseEventParser
import com.kashyap.wizlyai.network.models.TutorStreamEvent
import com.kashyap.wizlyai.ui.util.LatexToUnicode
import com.kashyap.wizlyai.ui.util.MathMarkdown
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
import kotlin.test.assertTrue

class SseParserTest {
    @Test
    fun parsesEventsAndMultilineData() {
        val parser = SseEventParser()
        val lines = listOf("event: delta", "data: {\"text\": \"a\"}", "", ": comment", "data: line1", "data: line2", "", "")
        val events = lines.mapNotNull { parser.feed(it) }
        assertEquals(listOf(SseEvent("delta", "{\"text\": \"a\"}"), SseEvent("message", "line1\nline2")), events)
    }

    @Test
    fun flushReturnsTrailingEvent() {
        val parser = SseEventParser()
        parser.feed("event: done")
        parser.feed("data: {}\r")
        assertEquals(SseEvent("done", "{}"), parser.flush())
    }
}

class TutorRepositoryTest {
    private fun repo(status: HttpStatusCode, body: String, contentType: String = "text/event-stream") =
        TutorRepository(
            client =
                HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }) {
                    install(ContentNegotiation) { json(JsonConfig.json) }
                    install(HttpTimeout)
                },
            baseUrl = { "http://test/api" },
        )

    @Test
    fun streamsDeltasThenDone() =
        runTest {
            val body =
                """
                event: start
                data: {"session_id": "s1", "user_message_id": "m1"}

                event: sources
                data: {"grounded": true, "count": 2}

                event: delta
                data: {"text": "Bayes [1]"}

                event: delta
                data: {"text": " rule."}

                event: done
                data: {"message": {"id": "m2", "role": "assistant", "content": "Bayes [1] rule.", "citations": [{"n": 1, "material_id": "mat", "title": "Slides", "page": 3}], "grounded": true}, "session": {"id": "s1", "title": "Bayes?"}}

                """.trimIndent() + "\n"
            val events = repo(HttpStatusCode.OK, body).ask("s1", "Bayes?").toList()
            assertIs<TutorStreamEvent.Started>(events[0])
            assertEquals(TutorStreamEvent.Sources(grounded = true, count = 2), events[1])
            assertEquals(listOf("Bayes [1]", " rule."), events.filterIsInstance<TutorStreamEvent.Delta>().map { it.text })
            val done = assertIs<TutorStreamEvent.Done>(events.last())
            assertEquals("p. 3", done.payload.message.citations.single().pageLabel)
        }

    @Test
    fun serverValidationErrorBecomesFailed() =
        runTest {
            val events = repo(HttpStatusCode.BadRequest, """{"success": false, "error": "Ask a question first"}""", "application/json").ask("s1", "").toList()
            assertEquals(listOf<TutorStreamEvent>(TutorStreamEvent.Failed("Ask a question first")), events)
        }

    @Test
    fun streamWithoutDoneReportsCutOff() =
        runTest {
            val events = repo(HttpStatusCode.OK, "event: delta\ndata: {\"text\": \"partial\"}\n\n").ask("s1", "q").toList()
            assertIs<TutorStreamEvent.Failed>(events.last())
        }
}

class LatexTest {
    @Test
    fun convertsCommonNotation() {
        assertEquals("P(A|B) = (P(B|A)P(A))/(P(B))", LatexToUnicode.convert("P(A|B) = \\frac{P(B|A)P(A)}{P(B)}"))
        assertEquals("∑ᵢ₌₁ⁿ xᵢ²", LatexToUnicode.convert("\\sum_{i=1}^{n} x_i^2"))
        assertEquals("∇f(x) ≈ 0", LatexToUnicode.convert("\\nabla f(x) \\approx 0"))
        assertEquals("√(x+1) ≤ ∛y", LatexToUnicode.convert("\\sqrt{x+1} \\le \\sqrt[3]{y}"))
        assertEquals("x ∈ ℝⁿ", LatexToUnicode.convert("x \\in \\mathbb{R}^n"))
        assertEquals("2x sin x + x² cos x", LatexToUnicode.convert("2x \\sin x + x^2 \\cos x"))
        assertEquals("lim x → 0 sin x/x", LatexToUnicode.convert("\\lim x \\to 0 \\sin x/x"))
        assertEquals("(2 0; 0 3)", LatexToUnicode.convert("\\begin{pmatrix}2&0\\\\0&3\\end{pmatrix}"))
        assertEquals("det [1 2; 3 4] = −2", LatexToUnicode.convert("\\det\\begin{bmatrix} 1 & 2 \\\\ 3 & 4 \\end{bmatrix} = -2"))
        assertEquals("|x| = { x, x ≥ 0; −x, x < 0 }", LatexToUnicode.convert("|x| = \\begin{cases} x & x \\geq 0 \\\\ -x & x < 0 \\end{cases}"))
        assertEquals("θ ← θ − α∇J(θ)", LatexToUnicode.convert("\\theta \\leftarrow \\theta - \\alpha \\nabla J(\\theta)"))
        assertEquals("e^(−λt)", LatexToUnicode.convert("e^{-\\lambda t}"))
        assertEquals("Var[X] = 𝔼[X²] − 𝔼[X]²", LatexToUnicode.convert("\\text{Var}[X] = \\mathbb{E}[X^2] - \\mathbb{E}[X]^2"))
    }

    @Test
    fun unknownCommandsStayReadable() {
        assertEquals("foo(x)", LatexToUnicode.convert("\\foo(x)"))
    }

    @Test
    fun markdownMathIsConvertedOutsideCode() {
        val md = "Loss is \$L = \\frac{1}{n}\\sum_i \\ell_i\$.\n\n\$\$\\hat{y} = Wx + b\$\$\n\n`\$not math\$` costs \$5 and \$10."
        val out = MathMarkdown.prepare(md)
        assertTrue(out.contains("Loss is L = 1/n∑ᵢ ℓᵢ."), out)
        assertTrue(out.contains("```math\ny\u0302 = Wx + b\n```"), out)
        assertTrue(out.contains("`\$not math\$`"), out)
        assertTrue(out.contains("costs \$5 and \$10."), out)
    }

    @Test
    fun inlineResultsDoNotBreakMarkdown() {
        assertEquals("Pick 2 or −2", MathMarkdown.prepare("Pick \$2\$ or \$-2\$"))
        val out = MathMarkdown.prepare("Use \$a*b\$ here")
        assertFalse(out.contains("\$"))
        assertTrue(out.contains("a\\*b"), out)
    }
}
