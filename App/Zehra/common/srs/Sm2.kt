package com.kashyap.wizlyai.srs

import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.days

/**
 * SM-2 spaced repetition, identical to the server's `app/services/srs.py`.
 *
 * Reviews done offline are scheduled here and replayed to the server later, so
 * both must agree exactly. Keep them in step: Sm2Test.kt and the server test
 * share the same vectors (Wizly-Server/tests/fixtures/srs_vectors.json).
 */
data class SrsState(
    val ease: Double = Sm2.DEFAULT_EASE,
    val intervalDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/** The review buttons and the SM-2 grade each one sends. */
enum class ReviewGrade(val grade: Int, val label: String) {
    AGAIN(1, "Again"),
    HARD(3, "Hard"),
    GOOD(4, "Good"),
    EASY(5, "Easy"),
}

object Sm2 {
    const val DEFAULT_EASE = 2.5
    const val MIN_EASE = 1.3
    const val MAX_INTERVAL_DAYS = 3650
    const val PASS_GRADE = 3

    // Half-up, like the server (floor(x + 0.5)); not banker's rounding
    private fun roundHalfUp(x: Double): Int = floor(x + 0.5).toInt()

    private fun roundEase(x: Double): Double = floor(x * 1000 + 0.5) / 1000

    /** The SM-2 step. The interval uses the ease from before this review. */
    fun next(state: SrsState, grade: Int): SrsState {
        require(grade in 0..5) { "grade must be 0-5" }
        var reps = state.reps
        var lapses = state.lapses
        val interval: Int
        if (grade < PASS_GRADE) {
            reps = 0
            interval = 1
            lapses += 1
        } else {
            interval =
                when (reps) {
                    0 -> 1
                    1 -> 6
                    else -> roundHalfUp(state.intervalDays * state.ease)
                }
            reps += 1
        }
        val q = 5 - grade
        val ease = max(MIN_EASE, roundEase(state.ease + 0.1 - q * (0.08 + q * 0.02)))
        return SrsState(ease = ease, intervalDays = min(max(interval, 1), MAX_INTERVAL_DAYS), reps = reps, lapses = lapses)
    }

    fun dueAfter(reviewedAt: Instant, state: SrsState): Instant = reviewedAt + state.intervalDays.days

    /** "1 day", "6 days", "3 mo", "1.2 yr": shown under each review button. */
    fun describeInterval(days: Int): String =
        when {
            days < 1 -> "now"
            days == 1 -> "1 day"
            days < 31 -> "$days days"
            days < 365 -> "${roundHalfUp(days / 30.0)} mo"
            else -> {
                val years = floor(days / 365.0 * 10 + 0.5) / 10
                if (years == floor(years)) "${years.toInt()} yr" else "$years yr"
            }
        }
}
