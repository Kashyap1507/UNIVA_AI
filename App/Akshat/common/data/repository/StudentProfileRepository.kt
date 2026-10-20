package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.network.models.StudentProfileData
import com.kashyap.wizlyai.network.models.StudentProfileResponse
import com.kashyap.wizlyai.network.models.StudentProfileUpdate
import com.kashyap.wizlyai.session.UserSessionManager
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Student profile (`/api/student/profile`). Successful calls also update
 * the student fields held by [UserSessionManager].
 */
class StudentProfileRepository {
    suspend fun getProfile(): Result<StudentProfileData> =
        runCatching {
            val response: StudentProfileResponse =
                ApiClient.httpClient.get("${NetworkConstants.getApiUrl()}/student/profile").body()
            unwrap(response)
        }

    suspend fun updateProfile(update: StudentProfileUpdate): Result<StudentProfileData> =
        runCatching {
            val response: StudentProfileResponse =
                ApiClient.httpClient.put("${NetworkConstants.getApiUrl()}/student/profile") {
                    contentType(ContentType.Application.Json)
                    setBody(toRequestBody(update))
                }.body()
            unwrap(response)
        }

    private fun unwrap(response: StudentProfileResponse): StudentProfileData {
        val data = response.data
        if (!response.success || data == null) {
            throw IllegalStateException(response.error ?: "Profile request failed")
        }
        UserSessionManager.instance.updateStudentState(
            profile = data.studentProfile,
            needsOnboarding = data.needsOnboarding,
            name = data.name,
        )
        return data
    }

    companion object {
        /** JSON body with only the fields being changed. */
        fun toRequestBody(update: StudentProfileUpdate): JsonObject =
            buildJsonObject {
                update.name?.let { put("name", JsonPrimitive(it.trim())) }
                update.university?.let { put("university", JsonPrimitive(it.trim())) }
                update.degreeLevel?.let { put("degree_level", JsonPrimitive(it.apiValue)) }
                update.program?.let { put("program", JsonPrimitive(it.trim())) }
                update.currentSemester?.let { put("current_semester", JsonPrimitive(it)) }
            }
    }
}
