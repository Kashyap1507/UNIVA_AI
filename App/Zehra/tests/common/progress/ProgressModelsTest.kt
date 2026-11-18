package com.kashyap.wizlyai.progress

import com.kashyap.wizlyai.navigation.BottomNavTab
import com.kashyap.wizlyai.navigation.NavigationController
import com.kashyap.wizlyai.navigation.Screen
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.CourseGrades
import com.kashyap.wizlyai.network.models.TodayData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressModelsTest {
    @Test
    fun decodesToday() {
        val json =
            """{"date": "2026-09-28",
            "streak": {"current": 3, "best": 5, "studied_today": true, "today_counts": {"cards": 4},
                       "last_7_days": [{"date": "2026-09-27", "active": true}, {"date": "2026-09-28", "active": true}]},
            "cards": {"due_total": 2, "due_by_course": {"c1": 2}},
            "overdue": [{"id": "d1", "course_id": "c1", "title": "Lab", "type": "LAB", "due_at": "2026-09-25", "all_day": true}],
            "upcoming": [],
            "plan_tasks": [{"id": "d1-t1", "type": "REVIEW_CARDS", "ref_id": "c1", "title": "Review due flashcards", "minutes": 15,
                            "done": false, "plan_id": "p1", "course_id": "c1", "plan_title": "Countdown to Midterm"}],
            "focus": {"concept_id": "k2", "concept_name": "Eigenvalues", "course_id": "c1", "mastery": 0.3, "reason": "WEAKEST"},
            "courses": {"c1": {"name": "Linear Algebra", "code": "MATH-2250", "color": null}}}"""
        val today = JsonConfig.json.decodeFromString(TodayData.serializer(), json)
        assertEquals(3, today.streak.current)
        assertEquals(4, today.streak.todayCounts["cards"])
        assertEquals("MATH-2250", today.courses.getValue("c1").short)
        assertEquals("p1", today.planTasks.single().planId)
        assertEquals(LocalDate.parse("2026-09-25"), today.overdue.single().localDate())
        assertEquals("WEAKEST", today.focus!!.reason)
    }

    @Test
    fun neededOnRemainingWeight() {
        // 75 % on 40 % of the grade so far (earned 30 of 40)
        val c = CourseGrades(courseId = "c1", gradedWeight = 40.0, remainingWeight = 60.0, earnedWeight = 30.0, currentPct = 75.0)
        assertEquals(83.3, (c.neededFor(80.0)!! * 10 + 0.5).toInt() / 10.0)
        assertEquals(true, c.neededFor(25.0)!! < 0) // already past 25 % of the final grade
        assertNull(c.copy(remainingWeight = 0.0).neededFor(80.0))
    }

    @Test
    fun tabsMapToUniversityScreens() {
        val nav = NavigationController()
        nav.navigateToBottomTab(BottomNavTab.COURSES)
        assertEquals(Screen.Courses, nav.currentScreen.value)
        assertEquals(BottomNavTab.COURSES, nav.getCurrentBottomTab())
        nav.navigateToBottomTab(BottomNavTab.TUTOR)
        assertEquals(BottomNavTab.TUTOR, nav.getCurrentBottomTab())
        nav.navigateToBottomTab(BottomNavTab.TODAY)
        assertEquals(Screen.Today, nav.currentScreen.value)
    }
}
