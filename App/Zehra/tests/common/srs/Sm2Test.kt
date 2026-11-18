package com.kashyap.wizlyai.srs

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Same vectors as Wizly-Server/tests/fixtures/srs_vectors.json. The server test
 * test_app_uses_the_same_vectors fails if this copy drifts from that file.
 */
private const val VECTORS_JSON = """
[
  {"name": "new card, good streak", "start": {"ease": 2.5, "interval_days": 0, "reps": 0, "lapses": 0}, "grades": [4, 4, 4, 4, 4], "expected": [{"ease": 2.5, "interval_days": 1, "reps": 1, "lapses": 0}, {"ease": 2.5, "interval_days": 6, "reps": 2, "lapses": 0}, {"ease": 2.5, "interval_days": 15, "reps": 3, "lapses": 0}, {"ease": 2.5, "interval_days": 38, "reps": 4, "lapses": 0}, {"ease": 2.5, "interval_days": 95, "reps": 5, "lapses": 0}]},
  {"name": "new card, easy streak", "start": {"ease": 2.5, "interval_days": 0, "reps": 0, "lapses": 0}, "grades": [5, 5, 5, 5], "expected": [{"ease": 2.6, "interval_days": 1, "reps": 1, "lapses": 0}, {"ease": 2.7, "interval_days": 6, "reps": 2, "lapses": 0}, {"ease": 2.8, "interval_days": 16, "reps": 3, "lapses": 0}, {"ease": 2.9, "interval_days": 45, "reps": 4, "lapses": 0}]},
  {"name": "hard answers lower ease", "start": {"ease": 2.5, "interval_days": 0, "reps": 0, "lapses": 0}, "grades": [3, 3, 3, 3, 3], "expected": [{"ease": 2.36, "interval_days": 1, "reps": 1, "lapses": 0}, {"ease": 2.22, "interval_days": 6, "reps": 2, "lapses": 0}, {"ease": 2.08, "interval_days": 13, "reps": 3, "lapses": 0}, {"ease": 1.94, "interval_days": 27, "reps": 4, "lapses": 0}, {"ease": 1.8, "interval_days": 52, "reps": 5, "lapses": 0}]},
  {"name": "lapse resets reps", "start": {"ease": 2.5, "interval_days": 0, "reps": 0, "lapses": 0}, "grades": [4, 4, 4, 1, 4, 4], "expected": [{"ease": 2.5, "interval_days": 1, "reps": 1, "lapses": 0}, {"ease": 2.5, "interval_days": 6, "reps": 2, "lapses": 0}, {"ease": 2.5, "interval_days": 15, "reps": 3, "lapses": 0}, {"ease": 1.96, "interval_days": 1, "reps": 0, "lapses": 1}, {"ease": 1.96, "interval_days": 1, "reps": 1, "lapses": 1}, {"ease": 1.96, "interval_days": 6, "reps": 2, "lapses": 1}]},
  {"name": "ease floor", "start": {"ease": 2.5, "interval_days": 0, "reps": 0, "lapses": 0}, "grades": [0, 1, 2, 0, 1, 3, 3], "expected": [{"ease": 1.7, "interval_days": 1, "reps": 0, "lapses": 1}, {"ease": 1.3, "interval_days": 1, "reps": 0, "lapses": 2}, {"ease": 1.3, "interval_days": 1, "reps": 0, "lapses": 3}, {"ease": 1.3, "interval_days": 1, "reps": 0, "lapses": 4}, {"ease": 1.3, "interval_days": 1, "reps": 0, "lapses": 5}, {"ease": 1.3, "interval_days": 1, "reps": 1, "lapses": 5}, {"ease": 1.3, "interval_days": 6, "reps": 2, "lapses": 5}]},
  {"name": "grade 0 to 5 from mature card", "start": {"ease": 2.36, "interval_days": 40, "reps": 5, "lapses": 1}, "grades": [0, 1, 2, 3, 4, 5], "expected": [{"ease": 1.56, "interval_days": 1, "reps": 0, "lapses": 2}, {"ease": 1.3, "interval_days": 1, "reps": 0, "lapses": 3}, {"ease": 1.3, "interval_days": 1, "reps": 0, "lapses": 4}, {"ease": 1.3, "interval_days": 1, "reps": 1, "lapses": 4}, {"ease": 1.3, "interval_days": 6, "reps": 2, "lapses": 4}, {"ease": 1.4, "interval_days": 8, "reps": 3, "lapses": 4}]},
  {"name": "half-up rounding (12.5 -> 13)", "start": {"ease": 2.5, "interval_days": 5, "reps": 2, "lapses": 0}, "grades": [4], "expected": [{"ease": 2.5, "interval_days": 13, "reps": 3, "lapses": 0}]},
  {"name": "interval cap", "start": {"ease": 2.8, "interval_days": 3000, "reps": 9, "lapses": 0}, "grades": [5, 5], "expected": [{"ease": 2.9, "interval_days": 3650, "reps": 10, "lapses": 0}, {"ease": 3.0, "interval_days": 3650, "reps": 11, "lapses": 0}]}
]"""

@Serializable
private data class StateDto(
    val ease: Double,
    @SerialName("interval_days") val intervalDays: Int,
    val reps: Int,
    val lapses: Int,
) {
    fun toState() = SrsState(ease, intervalDays, reps, lapses)
}

@Serializable
private data class Vector(val name: String, val start: StateDto, val grades: List<Int>, val expected: List<StateDto>)

class Sm2Test {
    @Test
    fun matchesServerVectors() {
        val vectors = Json.decodeFromString<List<Vector>>(VECTORS_JSON)
        assertEquals(8, vectors.size)
        for (v in vectors) {
            var state = v.start.toState()
            v.grades.zip(v.expected).forEachIndexed { i, (grade, expected) ->
                state = Sm2.next(state, grade)
                assertEquals(expected.toState(), state, "${v.name}, review ${i + 1}")
            }
        }
    }

    @Test
    fun rejectsBadGrades() {
        assertFailsWith<IllegalArgumentException> { Sm2.next(SrsState(), 6) }
        assertFailsWith<IllegalArgumentException> { Sm2.next(SrsState(), -1) }
    }

    @Test
    fun dueDateAndLabels() {
        val t = Instant.parse("2026-09-27T10:00:00Z")
        assertEquals(Instant.parse("2026-10-03T10:00:00Z"), Sm2.dueAfter(t, SrsState(intervalDays = 6)))
        assertEquals(listOf("1 day", "6 days", "3 mo", "1 yr", "1.5 yr"), listOf(1, 6, 95, 365, 550).map(Sm2::describeInterval))
    }
}
