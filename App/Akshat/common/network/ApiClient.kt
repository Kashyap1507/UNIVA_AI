package com.kashyap.wizlyai.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Ktor HTTP client for Wizly API
 */
object ApiClient {
    private var authToken: String? = null

    val httpClient =
        HttpClient {
            // Content Negotiation for JSON - uses shared JsonConfig
            install(ContentNegotiation) {
                json(JsonConfig.json)
            }

            // Logging
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
                // Never write the JWT to device logs
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }

            // Timeout Configuration
            install(HttpTimeout) {
                requestTimeoutMillis = NetworkConstants.READ_TIMEOUT
                connectTimeoutMillis = NetworkConstants.CONNECT_TIMEOUT
                socketTimeoutMillis = NetworkConstants.READ_TIMEOUT
            }

            // Default Request Configuration
            defaultRequest {
                url(NetworkConstants.getApiUrl())
                headers.append(NetworkConstants.Headers.CONTENT_TYPE, NetworkConstants.Headers.APPLICATION_JSON)
                headers.append(NetworkConstants.Headers.ACCEPT, NetworkConstants.Headers.APPLICATION_JSON)
                headers.append(NetworkConstants.Headers.USER_AGENT, NetworkConstants.Headers.USER_AGENT_VALUE)
                // The student's local day for streaks and "today" (IANA zone, e.g. America/Toronto)
                headers.append("X-Timezone", kotlinx.datetime.TimeZone.currentSystemDefault().id)

                // Add Authorization header if token is set
                authToken?.let { token ->
                    headers.append(NetworkConstants.Headers.AUTHORIZATION, "${NetworkConstants.Headers.BEARER_PREFIX}$token")
                }
            }

            // Authentication (removed - using manual header instead)
            // The Auth plugin's loadTokens is only called once during initialization,
            // so we manually add the token in defaultRequest which runs on every request
        }

    /**
     * Set authentication token
     */
    fun setAuthToken(token: String) {
        authToken = token
    }

    /**
     * Clear authentication token
     */
    fun clearAuthToken() {
        authToken = null
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return authToken != null
    }
}

// ========================================
// Documents API Extension Functions
// ========================================

/**
 * Response DTO for delete operations
 */
@kotlinx.serialization.Serializable
data class DeleteResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Delete a scanned document
 *
 * Path: DELETE /api/documents/{documentId}
 * Performs soft delete by default (marks deleted=true)
 *
 * @param documentId The ID of the document to delete
 * @param hardDelete If true, permanently delete. If false (default), soft delete
 * @return Unit on success
 * @throws Exception if deletion fails
 */
suspend fun HttpClient.deleteDocument(
    documentId: String,
    hardDelete: Boolean = false,
) {
    val response =
        delete("/api/documents/$documentId") {
            // Add hard_delete query parameter if requested
            if (hardDelete) {
                parameter("hard_delete", "true")
            }
        }

    // Parse response
    val apiResponse = response.body<DeleteResponse>()

    // Check if deletion was successful
    if (!apiResponse.success) {
        val errorMsg = apiResponse.message ?: apiResponse.error ?: "Failed to delete document"
        throw Exception(errorMsg)
    }
}

// ========================================
// FCM Token Registration API
// ========================================

/**
 * Request body for FCM token registration
 */
@kotlinx.serialization.Serializable
data class RegisterFCMTokenRequest(
    val fcm_token: String,
    val device_id: String,
    val platform: String,
    val device_model: String? = null,
    val app_version: String? = null,
)

/**
 * Response from FCM token registration
 */
@kotlinx.serialization.Serializable
data class FCMTokenResponse(
    val success: Boolean,
    val message: String,
    val device_id: String? = null,
)

/**
 * Register FCM token with backend for push notifications
 *
 * @param fcmToken The FCM token from Firebase
 * @param deviceId Unique device identifier
 * @param platform "android" or "ios"
 * @param deviceModel Optional device model name
 * @param appVersion Optional app version
 * @return FCMTokenResponse with success status
 * @throws Exception if registration fails
 */
suspend fun HttpClient.registerFCMToken(
    fcmToken: String,
    deviceId: String,
    platform: String,
    deviceModel: String? = null,
    appVersion: String? = null,
): FCMTokenResponse {
    return post("/api/fcm/token") {
        contentType(ContentType.Application.Json)
        setBody(
            RegisterFCMTokenRequest(
                fcm_token = fcmToken,
                device_id = deviceId,
                platform = platform.lowercase(),
                device_model = deviceModel,
                app_version = appVersion,
            ),
        )
    }.body()
}
