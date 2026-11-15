package com.kashyap.wizlyai.practice

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Countdown for timed mock exams.
 *
 * The server sets the deadline; the device clock may be off, so the countdown
 * runs from when the attempt started on this device plus the server's time
 * limit (deadline − started_at).
 */
object ExamTimer {
    /** Seconds left before the timer turns amber. */
    const val WARNING_SECONDS = 5 * 60L

    fun localDeadline(
        startedAt: Instant?,
        deadlineAt: Instant?,
        receivedAt: Instant,
    ): Instant? {
        if (startedAt == null || deadlineAt == null) return null
        val limit: Duration = deadlineAt - startedAt
        return if (limit.isPositive()) receivedAt + limit else null
    }

    fun remainingSeconds(
        deadline: Instant?,
        now: Instant,
    ): Long? = deadline?.let { (it - now).inWholeSeconds.coerceAtLeast(0) }

    fun isUp(
        deadline: Instant?,
        now: Instant,
    ): Boolean = deadline != null && now >= deadline

    fun isWarning(seconds: Long?): Boolean = seconds != null && seconds <= WARNING_SECONDS

    /** 3725 → "1:02:05", 272 → "04:32". */
    fun format(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        fun two(n: Long) = n.toString().padStart(2, '0')
        return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
    }
}
