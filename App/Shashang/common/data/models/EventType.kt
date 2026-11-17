package com.kashyap.wizlyai.data.models

import kotlinx.serialization.Serializable

/**
 * Kind of a calendar event read from the device calendar (or a deadline being reminded about)
 */
@Serializable
enum class EventType(val label: String) {
    EXAM("Exam"),
    QUIZ("Quiz"),
    ASSIGNMENT("Assignment"),
    PROJECT("Project"),
    PRESENTATION("Presentation"),
    LAB("Lab"),
    CLASS("Class"),
    HOLIDAY("Holiday"),
    OTHER("Deadline"),
    ;

    companion object {
        /** Best guess from an event title or description. */
        fun infer(text: String): EventType {
            val t = text.lowercase()
            return when {
                "exam" in t || "midterm" in t || "final" in t || "test" in t -> EXAM
                "quiz" in t -> QUIZ
                "presentation" in t -> PRESENTATION
                "project" in t -> PROJECT
                "lab" in t -> LAB
                "assignment" in t || "homework" in t || "essay" in t || "report" in t || "due" in t -> ASSIGNMENT
                "lecture" in t || "tutorial" in t || "class" in t || "seminar" in t -> CLASS
                "holiday" in t || "break" in t || "reading week" in t -> HOLIDAY
                else -> OTHER
            }
        }

        fun fromName(name: String?): EventType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
