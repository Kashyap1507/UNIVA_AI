package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.data.models.User
import com.kashyap.wizlyai.network.ApiClient
import com.kashyap.wizlyai.network.NetworkConstants
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Repository for authentication operations
 */
class AuthRepository {
    /**
     * Login with email and password
     */
    suspend fun login(
        email: String,
        password: String,
    ): Result<LoginResponse> {
        return try {
            val response =
                ApiClient.httpClient.post(NetworkConstants.LOGIN) {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(email, password))
                }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register a new student account
     */
    suspend fun register(
        email: String,
        password: String,
        fullName: String,
    ): Result<RegisterResponse> {
        return try {
            val response =
                ApiClient.httpClient.post(NetworkConstants.REGISTER) {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest(email, password, fullName))
                }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logout current user
     */
    suspend fun logout(): Result<Unit> {
        return try {
            ApiClient.httpClient.post(NetworkConstants.LOGOUT)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh authentication token
     */
    suspend fun refreshToken(refreshToken: String): Result<TokenResponse> {
        return try {
            val response =
                ApiClient.httpClient.post(NetworkConstants.REFRESH_TOKEN) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val user: User,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val email: String,
    val message: String,
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class TokenResponse(
    val token: String,
    val refreshToken: String,
)
