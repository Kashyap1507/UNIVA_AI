package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.NetworkConstants
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Manages FCM token registration with backend
 * Handles device token registration, refresh, and unregistration
 */
class FcmManager private constructor() {
    private var currentDeviceId: String? = null
    private var currentToken: String? = null

    companion object {
        val instance: FcmManager = FcmManager()
    }

    /**
     * Register FCM token with backend
     * Should be called after successful login and when FCM token is received
     *
     * @param deviceId Unique device identifier (generated or from device)
     * @param token FCM token from Firebase
     * @param deviceName Device model name (e.g., "Pixel 6", "iPhone 14")
     * @param platform Platform type ("android" or "ios")
     * @param appVersion App version string (e.g., "1.0.0")
     * @return Result with success status or error
     */
    suspend fun registerToken(
        deviceId: String,
        token: String,
        deviceName: String,
        platform: String,
        appVersion: String,
    ): Result<FcmTokenResponse> {
        return try {
            // Check if user is authenticated before registering
            if (!ApiClient.isAuthenticated()) {
                println("⚠️ FcmManager: Cannot register token - user not authenticated")
                return Result.failure(Exception("User not authenticated. Please login first."))
            }

            println("📲 FcmManager: Registering FCM token for device $deviceId")

            val url = "${NetworkConstants.getApiUrl()}/fcm/token"
            val response =
                ApiClient.httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        FcmTokenRequest(
                            device_id = deviceId,
                            fcm_token = token,
                            device_model = deviceName,
                            platform = platform,
                            app_version = appVersion,
                        ),
                    )
                }

            val result: FcmTokenResponse = response.body()

            // Store current registration
            currentDeviceId = deviceId
            currentToken = token

            val statusMessage = result.message ?: result.error ?: "Token registered"
            println("✅ FcmManager: $statusMessage")
            Result.success(result)
        } catch (e: Exception) {
            println("❌ FcmManager: Token registration failed - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Refresh FCM token with backend
     * Should be called when FCM token changes
     *
     * @param newToken New FCM token from Firebase
     * @return Result with success status or error
     */
    suspend fun refreshToken(newToken: String): Result<FcmTokenResponse> {
        return if (currentDeviceId != null) {
            println("🔄 FcmManager: Refreshing FCM token")
            registerToken(
                deviceId = currentDeviceId!!,
                token = newToken,
                deviceName = getPlatformDeviceName(),
                platform = getPlatformType(),
                appVersion = getAppVersion(),
            )
        } else {
            println("⚠️ FcmManager: Cannot refresh token - no device ID stored")
            Result.failure(Exception("No device ID stored. Register token first."))
        }
    }

    /**
     * Unregister FCM token from backend
     * Should be called on logout
     *
     * @return Result with success status or error
     */
    suspend fun unregisterToken(): Result<FcmUnregisterResponse> {
        return try {
            if (currentDeviceId == null) {
                println("⚠️ FcmManager: No device ID to unregister")
                return Result.success(
                    FcmUnregisterResponse(
                        success = true,
                        message = "No token to unregister",
                    ),
                )
            }

            println("🗑️ FcmManager: Unregistering FCM token for device $currentDeviceId")

            val url = "${NetworkConstants.getApiUrl()}/fcm/token"
            val response =
                ApiClient.httpClient.delete(url) {
                    parameter("device_id", currentDeviceId)
                }

            val result: FcmUnregisterResponse = response.body()

            // Clear stored registration
            currentDeviceId = null
            currentToken = null

            println("✅ FcmManager: Token unregistered successfully")
            Result.success(result)
        } catch (e: Exception) {
            println("❌ FcmManager: Token unregistration failed - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all registered FCM tokens for current user
     * Useful for debugging or managing multiple devices
     *
     * @return Result with list of registered tokens or error
     */
    suspend fun getRegisteredTokens(): Result<FcmTokenListResponse> {
        return try {
            println("📋 FcmManager: Fetching registered FCM tokens")

            val url = "${NetworkConstants.getApiUrl()}/fcm/token"
            val response = ApiClient.httpClient.get(url)
            val result: FcmTokenListResponse = response.body()

            println("✅ FcmManager: Retrieved ${result.tokens.size} registered tokens")
            Result.success(result)
        } catch (e: Exception) {
            println("❌ FcmManager: Failed to fetch tokens - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if FCM token is currently registered
     */
    fun isTokenRegistered(): Boolean {
        return currentDeviceId != null && currentToken != null
    }

    /**
     * Get current registered device ID
     */
    fun getCurrentDeviceId(): String? = currentDeviceId

    /**
     * Get current registered token
     */
    fun getCurrentToken(): String? = currentToken
}

// Platform-specific implementations (to be implemented with expect/actual)
internal expect fun getPlatformDeviceName(): String

internal expect fun getPlatformType(): String

internal expect fun getAppVersion(): String

// Request/Response models for FCM API

@Serializable
data class FcmTokenRequest(
    val device_id: String,
    val fcm_token: String,
    val device_model: String,
    val platform: String,
    val app_version: String,
)

@Serializable
data class FcmTokenResponse(
    val success: Boolean,
    val message: String? = null,
    val device_id: String? = null,
    val error: String? = null,
)

@Serializable
data class FcmUnregisterResponse(
    val success: Boolean,
    val message: String,
)

@Serializable
data class FcmTokenInfo(
    val device_id: String,
    val device_name: String,
    val platform: String,
    val app_version: String,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class FcmTokenListResponse(
    val success: Boolean,
    val tokens: List<FcmTokenInfo>,
)
