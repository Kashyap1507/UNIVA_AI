package com.kashyap.wizlyai.network

import com.kashyap.wizlyai.network.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Referral service for managing referral codes and tracking
 * Connects to backend /api/referrals endpoints
 */
class ReferralService {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = false
                    prettyPrint = true
                    coerceInputValues = true
                }
            )
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

        install(DefaultRequest) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }

    private val baseUrl = "${NetworkConstants.getApiUrl()}/referrals"

    /**
     * Validate a referral code (no auth required)
     * Use this before signup to check if code is valid and referrer has capacity
     *
     * @param code Referral code to validate (e.g., "ABC1XY")
     * @return ApiResponse with validation result
     */
    suspend fun validateReferralCode(code: String): ApiResponse<ValidateReferralResponse> {
        return try {
            val response = httpClient.post("$baseUrl/validate") {
                setBody(ValidateReferralRequest(referralCode = code))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val data: ValidateReferralResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = data,
                        message = data.message
                    )
                }
                HttpStatusCode.BadRequest -> {
                    ApiResponse(
                        success = false,
                        message = "Invalid referral code format",
                        error = "invalid_format"
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        message = "Failed to validate referral code",
                        error = "validation_failed"
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                message = e.message ?: "Network error",
                error = "network_error"
            )
        }
    }

    /**
     * Get user's own referral code (JWT required)
     * Returns the user's unique referral code and remaining capacity
     *
     * @param authToken JWT authentication token
     * @return ApiResponse with referral code info
     */
    suspend fun getMyReferralCode(authToken: String): ApiResponse<MyReferralCodeResponse> {
        return try {
            val response = httpClient.get("$baseUrl/my-code") {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val data: MyReferralCodeResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = data,
                        message = "Referral code retrieved successfully"
                    )
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResponse(
                        success = false,
                        message = "Authentication required",
                        error = "unauthorized"
                    )
                }
                HttpStatusCode.NotFound -> {
                    ApiResponse(
                        success = false,
                        message = "User not found",
                        error = "user_not_found"
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        message = "Failed to get referral code",
                        error = "fetch_failed"
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                message = e.message ?: "Network error",
                error = "network_error"
            )
        }
    }

    /**
     * Get list of users referred by current user (JWT required)
     * Returns all users who signed up using your referral code
     *
     * @param authToken JWT authentication token
     * @return ApiResponse with list of referred users
     */
    suspend fun getMyReferrals(authToken: String): ApiResponse<MyReferralsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/my-referrals") {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val data: MyReferralsResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = data,
                        message = "Referrals retrieved successfully"
                    )
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResponse(
                        success = false,
                        message = "Authentication required",
                        error = "unauthorized"
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        message = "Failed to get referrals",
                        error = "fetch_failed"
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                message = e.message ?: "Network error",
                error = "network_error"
            )
        }
    }

    /**
     * Get comprehensive referral statistics (JWT required)
     * Returns complete referral info including code, limits, stats, and referred users
     *
     * @param authToken JWT authentication token
     * @return ApiResponse with complete referral statistics
     */
    suspend fun getReferralStats(authToken: String): ApiResponse<ReferralStatsResponse> {
        return try {
            val response = httpClient.get("$baseUrl/stats") {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val data: ReferralStatsResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = data,
                        message = "Referral stats retrieved successfully"
                    )
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResponse(
                        success = false,
                        message = "Authentication required",
                        error = "unauthorized"
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        message = "Failed to get stats",
                        error = "fetch_failed"
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                message = e.message ?: "Network error",
                error = "network_error"
            )
        }
    }
}
