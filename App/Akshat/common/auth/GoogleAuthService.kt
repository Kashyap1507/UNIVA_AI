package com.kashyap.wizlyai.auth

import com.kashyap.wizlyai.data.models.User
import kotlinx.coroutines.flow.Flow

/**
 * Google Authentication Service Interface
 * Platform-specific implementations handle Google Sign-In
 */
interface GoogleAuthService {
    suspend fun signIn(): User?

    suspend fun signOut()

    fun isSignedIn(): Flow<Boolean>

    suspend fun getCurrentUser(): User?

    /**
     * Get the current Firebase ID token for the authenticated user
     * This token can be used for backend authentication
     */
    suspend fun getCurrentFirebaseToken(): String?
}
