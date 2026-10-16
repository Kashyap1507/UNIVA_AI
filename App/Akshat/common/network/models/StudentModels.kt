package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * University student profile, as returned by `/api/student/profile`,
 * auth responses and bootstrap data.
 */
@Serializable
data class StudentProfile(
    @SerialName("university") val university: String? = null,
    @SerialName("degree_level") val degreeLevel: String? = null,
    @SerialName("program") val program: String? = null,
    @SerialName("current_semester") val currentSemester: Int? = null,
    @SerialName("enrolled_course_ids") val enrolledCourseIds: List<String> = emptyList(),
) {
    val degree: DegreeLevel?
        get() = DegreeLevel.fromApi(degreeLevel)

    val isComplete: Boolean
        get() = !university.isNullOrBlank() && degree != null && !program.isNullOrBlank()
}

enum class DegreeLevel(val apiValue: String, val label: String) {
    BACHELOR("BACHELOR", "Bachelor's"),
    MASTER("MASTER", "Master's"),
    ;

    companion object {
        fun fromApi(value: String?): DegreeLevel? = entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class StudentProfileData(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("student_profile") val studentProfile: StudentProfile = StudentProfile(),
    @SerialName("needs_onboarding") val needsOnboarding: Boolean = true,
)

@Serializable
data class StudentProfileResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: StudentProfileData? = null,
    @SerialName("error") val error: String? = null,
)

/** Partial update; null fields are left unchanged by building the body without them. */
data class StudentProfileUpdate(
    val name: String? = null,
    val university: String? = null,
    val degreeLevel: DegreeLevel? = null,
    val program: String? = null,
    val currentSemester: Int? = null,
)
