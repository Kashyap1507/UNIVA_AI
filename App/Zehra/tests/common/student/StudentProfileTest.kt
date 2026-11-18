package com.kashyap.wizlyai.student

import com.kashyap.wizlyai.data.repository.StudentProfileRepository
import com.kashyap.wizlyai.network.models.BootstrapData
import com.kashyap.wizlyai.network.models.DegreeLevel
import com.kashyap.wizlyai.network.models.ProviderAuthResponse
import com.kashyap.wizlyai.network.models.SignupRequest
import com.kashyap.wizlyai.network.models.StudentProfile
import com.kashyap.wizlyai.network.models.StudentProfileResponse
import com.kashyap.wizlyai.network.models.StudentProfileUpdate
import com.kashyap.wizlyai.ui.screens.onboarding.StudentOnboardingForm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudentProfileTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesProfileResponse() {
        val body =
            """
            {"success": true, "data": {"id": "u1", "email": "s@uni.edu", "name": "Asha",
             "student_profile": {"university": "Windsor", "degree_level": "MASTER", "program": "MAC",
                                 "current_semester": 2, "enrolled_course_ids": []},
             "needs_onboarding": false, "extra": 1}}
            """.trimIndent()
        val data = assertNotNull(json.decodeFromString<StudentProfileResponse>(body).data)

        assertEquals(DegreeLevel.MASTER, data.studentProfile.degree)
        assertEquals(2, data.studentProfile.currentSemester)
        assertTrue(data.studentProfile.isComplete)
        assertFalse(data.needsOnboarding)
    }

    @Test
    fun missingStudentFieldsUseDefaults() {
        val profile = json.decodeFromString<StudentProfile>("""{"university": null}""")
        assertNull(profile.degree)
        assertFalse(profile.isComplete)
        assertTrue(profile.enrolledCourseIds.isEmpty())
    }

    @Test
    fun authAndBootstrapCarryStudentFields() {
        val auth =
            json.decodeFromString<ProviderAuthResponse>(
                """{"message": "ok", "needs_onboarding": true, "student_profile": {"program": "CS"}}""",
            )
        assertTrue(auth.needsOnboarding)
        assertEquals("CS", auth.studentProfile?.program)

        val bootstrap =
            json.decodeFromString<BootstrapData>(
                """{"sync_timestamp": "t", "children": [{"id": "c9"}], "grades": [], "needs_onboarding": false}""",
            )
        // Old K-12 keys from an older server are ignored
        assertEquals("t", bootstrap.syncTimestamp)
        assertFalse(bootstrap.needsOnboarding)
    }

    @Test
    fun signupRequestHasNoChildFields() {
        val encoded = Json.encodeToString(SignupRequest(email = "s@uni.edu", referralCode = "ABC"))
        assertFalse(encoded.contains("child"))
    }

    @Test
    fun updateBodyOnlyContainsChangedFields() {
        val body = StudentProfileRepository.toRequestBody(StudentProfileUpdate(program = " MAC ", degreeLevel = DegreeLevel.BACHELOR))
        assertEquals(setOf("program", "degree_level"), body.keys)
        assertEquals("MAC", body["program"]?.jsonPrimitive?.content)
        assertEquals("BACHELOR", body["degree_level"]?.jsonPrimitive?.content)
    }

    @Test
    fun onboardingFormValidation() {
        val empty = StudentOnboardingForm()
        assertFalse(empty.isValid)

        val filled = StudentOnboardingForm(name = "Asha", university = "Windsor", degreeLevel = DegreeLevel.MASTER, program = "MAC")
        assertTrue(filled.isValid)
        assertFalse(filled.copy(degreeLevel = null).isValid)
        assertFalse(filled.copy(name = "A").isValid)
        assertNotNull(filled.copy(program = "x".repeat(121)).programError)

        val update = filled.copy(currentSemester = 3).toUpdate()
        assertEquals(3, update.currentSemester)
        assertEquals(DegreeLevel.MASTER, update.degreeLevel)
    }

    @Test
    fun emailDerivedNameIsNotPrefilled() {
        val form = StudentOnboardingForm.from(StudentProfile(), name = "jane.doe", email = "jane.doe@uni.edu")
        assertEquals("", form.name)
        assertEquals("Jane", StudentOnboardingForm.from(StudentProfile(), name = "Jane", email = "jane.doe@uni.edu").name)
    }

    @Test
    fun formPrefillsFromProfile() {
        val form =
            StudentOnboardingForm.from(
                StudentProfile(university = "Windsor", degreeLevel = "bachelor", program = "CS", currentSemester = 4),
                name = "Ravi",
            )
        assertEquals(DegreeLevel.BACHELOR, form.degreeLevel)
        assertEquals("Ravi", form.name)
        assertTrue(form.isValid)
        assertEquals(setOf("name", "university", "degree_level", "program", "current_semester"), StudentProfileRepository.toRequestBody(form.toUpdate()).jsonObject.keys)
    }
}
