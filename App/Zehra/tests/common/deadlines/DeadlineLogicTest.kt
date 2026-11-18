package com.kashyap.wizlyai.deadlines

import com.kashyap.wizlyai.data.models.CalendarEvent
import com.kashyap.wizlyai.data.models.EventType
import com.kashyap.wizlyai.network.JsonConfig
import com.kashyap.wizlyai.network.models.Deadline
import com.kashyap.wizlyai.network.models.DeadlineType
import com.kashyap.wizlyai.network.models.StudyPlan
import com.kashyap.wizlyai.network.models.SyllabusCandidate
import com.kashyap.wizlyai.ui.viewmodel.StudyPlanViewModel
import com.kashyap.wizlyai.ui.viewmodel.SyllabusImportViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeadlineLogicTest {
    private val today = LocalDate.parse("2026-09-27")
    private val toronto = TimeZone.of("America/Toronto")

    private fun d(
        id: String,
        due: String?,
        completed: Boolean = false,
        allDay: Boolean = due?.length == 10,
    ) = Deadline(id = id, title = id, dueAt = due, allDay = allDay, completed = completed)

    @Test
    fun timedDeadlinesUseTheLocalDate() {
        // 02:30 UTC on the 28th is still the 27th in Toronto
        val late = d("late", "2026-09-28T02:30:00Z")
        assertEquals(LocalDate.parse("2026-09-27"), late.localDate(toronto))
        assertEquals("22:30", late.localTime(toronto))
        assertNull(d("allday", "2026-10-01").localTime(toronto))
    }

    @Test
    fun groupsIntoSectionsInOrder() {
        val items =
            listOf(
                d("later", "2026-10-20"),
                d("soon", "2026-10-02"),
                d("today", "2026-09-28T02:30:00Z"),
                d("overdue", "2026-09-20"),
                d("nodate", null),
                d("done", "2026-09-10", completed = true),
            )
        val groups = DeadlineLogic.group(items, today, toronto)
        assertEquals(
            listOf(DeadlineSection.OVERDUE, DeadlineSection.TODAY, DeadlineSection.THIS_WEEK, DeadlineSection.LATER, DeadlineSection.NO_DATE, DeadlineSection.DONE),
            groups.map { it.first },
        )
        assertEquals(listOf("today"), groups[1].second.map { it.id })
    }

    @Test
    fun countdownLabels() {
        val labels = listOf(0, 1, -1, -3, 5, 20, 21, 45, 100).map { DeadlineLogic.countdown(DeadlineLogic.addDays(today, it), today) }
        assertEquals(listOf("Today", "Tomorrow", "Yesterday", "3 days ago", "in 5 days", "in 20 days", "in 3 weeks", "in 6 weeks", "in 3 months"), labels)
        assertNull(DeadlineLogic.countdown(null, today))
    }

    @Test
    fun typeInferenceMatchesServer() {
        val cases =
            mapOf(
                "Final presentation" to DeadlineType.PRESENTATION,
                "Final Exam" to DeadlineType.EXAM,
                "Lab quiz 2" to DeadlineType.QUIZ,
                "Lab report 4 is due" to DeadlineType.LAB,
                "Problem set 3" to DeadlineType.ASSIGNMENT,
                "Reading" to DeadlineType.OTHER,
            )
        cases.forEach { (title, type) -> assertEquals(type, DeadlineLogic.inferType(title), title) }
    }

    @Test
    fun deviceEventsBecomeDeadlinesWithoutDuplicates() {
        val events =
            listOf(
                CalendarEvent(title = "MATH 2250 Midterm", date = "2026-10-20", time = "14:00", type = EventType.EXAM),
                CalendarEvent(title = "Lab report", date = "2026-10-05", type = EventType.OTHER),
                CalendarEvent(title = "Old quiz", date = "2026-09-01", type = EventType.QUIZ),
                CalendarEvent(title = "Essay 1", date = "2026-10-10", type = EventType.ASSIGNMENT),
            )
        val existing = listOf(d("Essay 1", "2026-10-10"))
        val inputs = DeadlineLogic.newDeviceEvents(events, existing, today, toronto)
        assertEquals(listOf("MATH 2250 Midterm", "Lab report"), inputs.map { it.title })
        assertEquals("EXAM", inputs[0].type)
        assertEquals("2026-10-20T18:00:00Z", inputs[0].dueAt) // 14:00 EDT
        assertEquals("LAB", inputs[1].type)
        assertEquals("2026-10-05", inputs[1].dueAt)
    }

    @Test
    fun syllabusPreviewTicksOnlyNewDatedItems() {
        val preview =
            SyllabusImportViewModel.toPreview(
                "Syllabus",
                listOf(
                    SyllabusCandidate(title = "Assignment 1", type = "ASSIGNMENT", dueAt = "2026-10-03"),
                    SyllabusCandidate(title = "Midterm", type = "EXAM", dueAt = "2026-10-20", duplicateOf = "d1"),
                    SyllabusCandidate(title = "Final exam", type = "EXAM", dueAt = null, weightPct = 40.0),
                ),
            )
        assertEquals(setOf(0), preview.selected)
        assertEquals(mapOf(1 to "Already saved", 2 to "No date in the syllabus"), preview.notes)
        assertEquals("SYLLABUS", preview.source)
    }

    @Test
    fun planDecodesAndFindsToday() {
        val json =
            """{"id": "p1", "course_id": "c1", "exam_date": "2026-10-07", "title": "Countdown to Midterm", "minutes_per_day": 90,
            "tasks_total": 2, "tasks_done": 0, "days": [
              {"date": "2026-09-26", "tasks": [{"id": "d1-t1", "type": "LEARN", "ref_id": "k1", "title": "Study: Bases", "minutes": 25, "done": false}]},
              {"date": "2026-09-27", "tasks": [{"id": "d2-t1", "type": "REVIEW_CARDS", "ref_id": "c1", "title": "Review due flashcards", "minutes": 15, "done": true}]}
            ]}"""
        val plan = JsonConfig.json.decodeFromString(StudyPlan.serializer(), json)
        assertEquals(1, StudyPlanViewModel.todayIndex(plan, today))
        assertEquals(-1, StudyPlanViewModel.todayIndex(plan, LocalDate.parse("2026-12-01")))
        assertEquals(true, plan.days!![1].tasks[0].done)
    }
}
