package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreakDay(
    @SerialName("date") val date: String,
    @SerialName("active") val active: Boolean = false,
)

@Serializable
data class StudyStreak(
    @SerialName("current") val current: Int = 0,
    @SerialName("best") val best: Int = 0,
    @SerialName("studied_today") val studiedToday: Boolean = false,
    @SerialName("today_counts") val todayCounts: Map<String, Int> = emptyMap(),
    @SerialName("last_7_days") val last7Days: List<StreakDay> = emptyList(),
)

@Serializable
data class TodayTask(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("ref_id") val refId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("minutes") val minutes: Int = 0,
    @SerialName("done") val done: Boolean = false,
    @SerialName("plan_id") val planId: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("plan_title") val planTitle: String? = null,
)

@Serializable
data class TodayFocus(
    @SerialName("concept_id") val conceptId: String,
    @SerialName("concept_name") val conceptName: String? = null,
    @SerialName("course_id") val courseId: String,
    @SerialName("mastery") val mastery: Double = 0.0,
    /** WEAKEST or NOT_PRACTISED. */
    @SerialName("reason") val reason: String,
    @SerialName("exam_title") val examTitle: String? = null,
)

@Serializable
data class CourseLabel(
    @SerialName("name") val name: String,
    @SerialName("code") val code: String? = null,
    @SerialName("color") val color: String? = null,
) {
    val short: String get() = code?.takeIf { it.isNotBlank() } ?: name
}

@Serializable
data class TodayData(
    @SerialName("date") val date: String,
    @SerialName("streak") val streak: StudyStreak = StudyStreak(),
    @SerialName("cards") val cards: FlashcardSummary = FlashcardSummary(),
    @SerialName("overdue") val overdue: List<Deadline> = emptyList(),
    @SerialName("upcoming") val upcoming: List<Deadline> = emptyList(),
    @SerialName("plan_tasks") val planTasks: List<TodayTask> = emptyList(),
    @SerialName("focus") val focus: TodayFocus? = null,
    @SerialName("courses") val courses: Map<String, CourseLabel> = emptyMap(),
)

@Serializable
data class ConceptStats(
    @SerialName("total") val total: Int = 0,
    @SerialName("practised") val practised: Int = 0,
    @SerialName("mastery_avg") val masteryAvg: Double? = null,
)

@Serializable
data class CardStats(
    @SerialName("total") val total: Int = 0,
    @SerialName("due") val due: Int = 0,
    @SerialName("mature") val mature: Int = 0,
)

@Serializable
data class PracticeStats(
    @SerialName("attempts") val attempts: Int = 0,
    @SerialName("average_pct") val averagePct: Double? = null,
    @SerialName("last_pct") val lastPct: Double? = null,
)

@Serializable
data class CourseProgress(
    @SerialName("course_id") val courseId: String,
    @SerialName("name") val name: String,
    @SerialName("code") val code: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("concepts") val concepts: ConceptStats = ConceptStats(),
    @SerialName("cards") val cards: CardStats = CardStats(),
    @SerialName("reviews_7d") val reviews7d: Int = 0,
    @SerialName("practice") val practice: PracticeStats = PracticeStats(),
    @SerialName("next_deadline") val nextDeadline: Deadline? = null,
    @SerialName("grade_pct") val gradePct: Double? = null,
)

@Serializable
data class ConceptProgress(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("mastery") val mastery: Double = 0.0,
    @SerialName("attempts") val attempts: Int = 0,
    @SerialName("practised") val practised: Boolean = false,
)

@Serializable
data class ReviewDay(
    @SerialName("date") val date: String,
    @SerialName("count") val count: Int = 0,
)

@Serializable
data class PracticePoint(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("kind") val kind: String? = null,
    @SerialName("score_pct") val scorePct: Double? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class GradeEntry(
    @SerialName("id") val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("name") val name: String,
    @SerialName("score") val score: Double,
    @SerialName("max") val max: Double,
    @SerialName("weight_pct") val weightPct: Double,
    @SerialName("percent") val percent: Double? = null,
)

@Serializable
data class CourseGrades(
    @SerialName("course_id") val courseId: String,
    @SerialName("course_name") val courseName: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("graded_weight") val gradedWeight: Double = 0.0,
    @SerialName("remaining_weight") val remainingWeight: Double = 100.0,
    @SerialName("earned_weight") val earnedWeight: Double = 0.0,
    @SerialName("current_pct") val currentPct: Double? = null,
    @SerialName("grade_point") val gradePoint: Double? = null,
    @SerialName("letter") val letter: String? = null,
    @SerialName("credits") val credits: Double = 3.0,
    @SerialName("entries") val entries: List<GradeEntry> = emptyList(),
) {
    /** Average % needed on the ungraded weight to finish on [targetPct]; null when nothing is left. */
    fun neededFor(targetPct: Double): Double? =
        if (remainingWeight <= 0.0) null else (100 * (targetPct - earnedWeight) / remainingWeight)
}

@Serializable
data class GradesOverview(
    @SerialName("courses") val courses: List<CourseGrades> = emptyList(),
    @SerialName("gpa") val gpa: Double? = null,
    @SerialName("gpa_credits") val gpaCredits: Double = 0.0,
)

@Serializable
data class CourseProgressDetail(
    @SerialName("course_id") val courseId: String,
    @SerialName("name") val name: String,
    @SerialName("code") val code: String? = null,
    @SerialName("concepts") val concepts: List<ConceptProgress> = emptyList(),
    @SerialName("concept_stats") val conceptStats: ConceptStats = ConceptStats(),
    @SerialName("cards") val cards: CardStats = CardStats(),
    @SerialName("reviews_per_day") val reviewsPerDay: List<ReviewDay> = emptyList(),
    @SerialName("practice") val practice: PracticeStats = PracticeStats(),
    @SerialName("practice_history") val practiceHistory: List<PracticePoint> = emptyList(),
    @SerialName("grades") val grades: CourseGrades? = null,
)

@Serializable
data class GradeInput(
    @SerialName("course_id") val courseId: String,
    @SerialName("name") val name: String,
    @SerialName("score") val score: Double,
    @SerialName("max") val max: Double,
    @SerialName("weight_pct") val weightPct: Double,
)
