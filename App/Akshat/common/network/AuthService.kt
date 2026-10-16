package com.kashyap.wizlyai.network

import com.kashyap.wizlyai.network.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
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
import io.ktor.client.plugins.timeout

/**
 * Authentication service for Wizly backend API
 * Wizly AI server authentication endpoints
 */
class AuthService {
    private val httpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = false
                        prettyPrint = true
                        coerceInputValues = true
                    },
                )
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }

            install(HttpTimeout) {
                // Default timeout for most auth operations (signup, login, verify)
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
                // Note: Provider auth endpoint uses a per-request timeout override
                // (it returns the bootstrap data with the token)
            }

            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }

    private val baseUrl = "${NetworkConstants.getApiUrl()}/auth"

    /**
     * Initiate signup process - sends verification code to email
     */
    suspend fun initiateSignup(request: SignupRequest): ApiResponse<SignupResponse> {
        return try {
            val response =
                httpClient.post("$baseUrl/signup") {
                    setBody(request)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val signupResponse: SignupResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = signupResponse,
                        message = "Verification code sent successfully",
                    )
                }
                HttpStatusCode.BadRequest -> {
                    try {
                        val errorResponse: AuthErrorResponse = response.body()
                        ApiResponse(
                            success = false,
                            data = null,
                            error = errorResponse.error,
                            message = errorResponse.message,
                        )
                    } catch (e: Exception) {
                        ApiResponse(
                            success = false,
                            data = null,
                            error = "Bad request",
                            message = "Invalid signup data",
                        )
                    }
                }
                HttpStatusCode.Conflict -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Email already exists",
                        message = "An account with this email already exists",
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Signup failed",
                        message = "Server returned status: ${response.status}",
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                data = null,
                error = "Network error",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    /**
     * Initiate login process - sends verification code to email
     */
    suspend fun initiateLogin(request: LoginRequest): ApiResponse<LoginResponse> {
        return try {
            val response =
                httpClient.post("$baseUrl/login") {
                    setBody(request)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val loginResponse: LoginResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = loginResponse,
                        message = "Login code sent successfully",
                    )
                }
                HttpStatusCode.BadRequest -> {
                    try {
                        val errorResponse: AuthErrorResponse = response.body()
                        ApiResponse(
                            success = false,
                            data = null,
                            error = errorResponse.error,
                            message = errorResponse.message,
                        )
                    } catch (e: Exception) {
                        ApiResponse(
                            success = false,
                            data = null,
                            error = "Bad request",
                            message = "Invalid login data",
                        )
                    }
                }
                HttpStatusCode.NotFound -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "User not found",
                        message = "No account found with this email",
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Login failed",
                        message = "Server returned status: ${response.status}",
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                data = null,
                error = "Network error",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    /**
     * Verify email code and complete authentication
     */
    suspend fun verifyEmail(request: VerifyEmailRequest): ApiResponse<VerifyEmailResponse> {
        return try {
            val response =
                httpClient.post("$baseUrl/verify-email") {
                    setBody(request)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val verifyResponse: VerifyEmailResponse = response.body()
                    ApiResponse(
                        success = true,
                        data = verifyResponse,
                        message = verifyResponse.message,
                    )
                }
                HttpStatusCode.BadRequest -> {
                    try {
                        val errorResponse: AuthErrorResponse = response.body()
                        ApiResponse(
                            success = false,
                            data = null,
                            error = errorResponse.error,
                            message = errorResponse.message,
                        )
                    } catch (e: Exception) {
                        ApiResponse(
                            success = false,
                            data = null,
                            error = "Bad request",
                            message = "Invalid verification data",
                        )
                    }
                }
                HttpStatusCode.Unauthorized -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Invalid verification code",
                        message = "The verification code is incorrect or expired",
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Verification failed",
                        message = "Server returned status: ${response.status}",
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                data = null,
                error = "Network error",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    /**
     * Authenticate with provider (Google, Facebook, Apple)
     */
    suspend fun authenticateWithProvider(request: ProviderAuthRequest): ApiResponse<ProviderAuthResponse> {
        return try {
            // 🔍 DEBUG: Log request details
            val fullUrl = "$baseUrl/provider"
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("🌐 [AUTH] Provider Auth Request")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📍 URL: $fullUrl")
            println("📦 Provider: ${request.provider}")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            val response =
                httpClient.post(fullUrl) {
                    setBody(request)
                    // Extended timeout for provider auth (account creation + bootstrap data)
                    timeout {
                        requestTimeoutMillis = NetworkConstants.AUTH_OPERATION_TIMEOUT
                        connectTimeoutMillis = NetworkConstants.AUTH_OPERATION_TIMEOUT
                        socketTimeoutMillis = NetworkConstants.AUTH_OPERATION_TIMEOUT
                    }
                }

            println("✅ [AUTH] Response received - Status: ${response.status}")

            when (response.status) {
                HttpStatusCode.OK -> {
                    val providerResponse: ProviderAuthResponse = response.body()
                    println("✅ [AUTH] Provider authentication successful")
                    ApiResponse(
                        success = true,
                        data = providerResponse,
                        message = "Provider authentication successful",
                    )
                }
                HttpStatusCode.BadRequest -> {
                    try {
                        val errorResponse: AuthErrorResponse = response.body()
                        println("❌ [AUTH] Bad Request - ${errorResponse.error}: ${errorResponse.message}")
                        ApiResponse(
                            success = false,
                            data = null,
                            error = errorResponse.error,
                            message = errorResponse.message,
                        )
                    } catch (e: Exception) {
                        println("❌ [AUTH] Bad Request - Failed to parse error response: ${e.message}")
                        ApiResponse(
                            success = false,
                            data = null,
                            error = "Bad request",
                            message = "Invalid provider authentication data",
                        )
                    }
                }
                HttpStatusCode.Unauthorized -> {
                    println("❌ [AUTH] Unauthorized - Firebase token invalid or expired")
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Invalid token",
                        message = "Firebase token is invalid or expired",
                    )
                }
                else -> {
                    println("❌ [AUTH] Unexpected status: ${response.status}")
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Provider authentication failed",
                        message = "Server returned status: ${response.status}",
                    )
                }
            }
        } catch (e: Exception) {
            // 🔍 DEBUG: Enhanced exception logging
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("❌ [AUTH] EXCEPTION CAUGHT")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("🔥 Exception Type: ${e::class.simpleName}")
            println("💬 Exception Message: ${e.message}")
            println("📍 Full URL: $baseUrl/provider")
            println("🔍 Stack Trace:")
            e.printStackTrace()
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            ApiResponse(
                success = false,
                data = null,
                error = "Network error",
                message = "${e::class.simpleName}: ${e.message ?: "Unknown network error"}",
            )
        }
    }

    /**
     * Logout user
     */
    suspend fun logout(): ApiResponse<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/logout")

            when (response.status) {
                HttpStatusCode.OK -> {
                    ApiResponse(
                        success = true,
                        data = Unit,
                        message = "Logged out successfully",
                    )
                }
                else -> {
                    ApiResponse(
                        success = false,
                        data = null,
                        error = "Logout failed",
                        message = "Server returned status: ${response.status}",
                    )
                }
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                data = null,
                error = "Network error",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    /**
     * Close the HTTP client when done
     */
    fun close() {
        httpClient.close()
    }
}
