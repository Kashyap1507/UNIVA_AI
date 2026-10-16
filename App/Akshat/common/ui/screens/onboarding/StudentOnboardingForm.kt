package com.kashyap.wizlyai.ui.screens.onboarding

import com.kashyap.wizlyai.network.models.DegreeLevel
import com.kashyap.wizlyai.network.models.StudentProfile
import com.kashyap.wizlyai.network.models.StudentProfileUpdate

/**
 * Plain form state for student onboarding / profile editing.
 * Kept free of Compose so the rules can be unit tested.
 */
data class StudentOnboardingForm(
    val name: String = "",
    val university: String = "",
    val degreeLevel: DegreeLevel? = null,
    val program: String = "",
    val currentSemester: Int? = null,
) {
    val nameError: String?
        get() = if (name.isNotBlank() && name.trim().length < 2) "Name must be at least 2 characters" else null

    val universityError: String?
        get() = lengthError(university, "University")

    val programError: String?
        get() = lengthError(program, "Program")

    val isValid: Boolean
        get() =
            name.trim().length >= 2 &&
                university.isNotBlank() && universityError == null &&
                degreeLevel != null &&
                program.isNotBlank() && programError == null

    fun toUpdate(): StudentProfileUpdate =
        StudentProfileUpdate(
            name = name.trim(),
            university = university.trim(),
            degreeLevel = degreeLevel,
            program = program.trim(),
            currentSemester = currentSemester,
        )

    companion object {
        const val MAX_TEXT_LENGTH = 120
        val SEMESTERS = 1..12

        /**
         * Prefill from the saved profile. The server falls back to the email's
         * local part when no name is set; that isn't a real name, so skip it.
         */
        fun from(profile: StudentProfile, name: String?, email: String? = null): StudentOnboardingForm =
            StudentOnboardingForm(
                name = name?.takeUnless { email != null && it == email.substringBefore("@") }.orEmpty(),
                university = profile.university.orEmpty(),
                degreeLevel = profile.degree,
                program = profile.program.orEmpty(),
                currentSemester = profile.currentSemester,
            )

        private fun lengthError(value: String, label: String): String? =
            if (value.trim().length > MAX_TEXT_LENGTH) "$label must be at most $MAX_TEXT_LENGTH characters" else null
    }
}
