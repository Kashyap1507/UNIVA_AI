package com.kashyap.wizlyai.session

import com.kashyap.wizlyai.data.models.User
import com.kashyap.wizlyai.data.preferences.PreferenceKeys
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.UserProfile
import com.kashyap.wizlyai.network.models.StudentProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user session state and JWT token lifecycle
 * Singleton for app-wide access to current user and authentication state
 * Also caches the profile from the auth response for instant access
 */
class UserSessionManager private constructor() {
    // Preferences manager (must be initialized from platform code)
    private var preferencesManager: PreferencesManager? = null

    companion object {
        val instance: UserSessionManager = UserSessionManager()
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    }

    // Current authenticated user
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    // JWT authentication token
    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    // Session expiration timestamp (milliseconds since epoch)
    private val _tokenExpiresAt = MutableStateFlow<Long>(0)
    val tokenExpiresAt: StateFlow<Long> = _tokenExpiresAt.asStateFlow()

    // Cached profile for instant access
    private val _cachedUserProfile = MutableStateFlow<User?>(null)
    val cachedUserProfile: StateFlow<User?> = _cachedUserProfile.asStateFlow()

    // Cache timestamp to track freshness
    private var cacheTimestamp: Long = 0

    // Is user authenticated
    val isAuthenticated: Boolean
        get() = _currentUser.value != null && _authToken.value != null

    // User ID convenience accessor
    val userId: String?
        get() = _currentUser.value?.id

    // User email convenience accessor
    val userEmail: String?
        get() = _currentUser.value?.email

    // Student display name from cached profile or auth response
    val userName: String?
        get() = _cachedUserProfile.value?.name ?: _currentUser.value?.name

    // --- University student state -------------------------------------------

    private val _studentProfile = MutableStateFlow(StudentProfile())
    val studentProfile: StateFlow<StudentProfile> = _studentProfile.asStateFlow()

    private val _needsOnboarding = MutableStateFlow(false)
    val needsOnboarding: StateFlow<Boolean> = _needsOnboarding.asStateFlow()

    /**
     * Store the student fields from an auth response, bootstrap data or the
     * profile API. The onboarding flag is persisted so a restored session
     * still routes to onboarding if it wasn't finished.
     */
    fun updateStudentState(
        profile: StudentProfile?,
        needsOnboarding: Boolean,
        name: String? = null,
    ) {
        _studentProfile.value = profile ?: StudentProfile()
        _needsOnboarding.value = needsOnboarding
        preferencesManager?.putBoolean(PreferenceKeys.STUDENT_NEEDS_ONBOARDING, needsOnboarding)
        if (!name.isNullOrBlank()) {
            _currentUser.value = _currentUser.value?.copy(name = name)
            _cachedUserProfile.value = _cachedUserProfile.value?.copy(name = name)
        }
    }

    /** Persisted onboarding flag, used when restoring a session at app start. */
    fun storedNeedsOnboarding(): Boolean =
        preferencesManager?.getBoolean(PreferenceKeys.STUDENT_NEEDS_ONBOARDING, false) ?: false

    // Check if cache is still valid
    val isCacheValid: Boolean
        get() {
            val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            return (currentTime - cacheTimestamp) < CACHE_VALIDITY_MS
        }

    /**
     * Initialize the session manager with PreferencesManager
     * Call this once at app startup
     */
    fun initialize(prefs: PreferencesManager) {
        preferencesManager = prefs
    }

    /**
     * Initialize session with user and token from authentication
     */
    fun startSession(
        user: AuthUser,
        token: String,
        expiresAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + (7 * 24 * 60 * 60 * 1000),
    ) {
        _currentUser.value = user
        _authToken.value = token
        _tokenExpiresAt.value = expiresAt

        // Persist to preferences for app restarts
        preferencesManager?.apply {
            putString(PreferenceKeys.USER_ID, user.id)
            putString(PreferenceKeys.USER_EMAIL, user.email)
            putString(PreferenceKeys.AUTH_PROVIDER, user.authProvider)
            putString(PreferenceKeys.AUTH_TOKEN, token)
            putLong(PreferenceKeys.TOKEN_EXPIRES_AT, expiresAt)
        }

        // Set token in ApiClient
        com.kashyap.wizlyai.network.ApiClient.setAuthToken(token)
    }

    /**
     * Cache the profile from the auth response (user_profile)
     */
    fun cacheUserProfile(userProfile: UserProfile?) {
        if (userProfile != null) {
            _cachedUserProfile.value =
                User(
                    id = userProfile.id,
                    name = userProfile.name ?: userProfile.email.substringBefore("@"),
                    email = userProfile.email,
                )
            cacheTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }

    /**
     * Restore session from persisted preferences
     * Call this on app startup
     */
    fun restoreSession(): Boolean {
        val prefs = preferencesManager ?: return false

        val userId = prefs.getString(PreferenceKeys.USER_ID, "")
        val userEmail = prefs.getString(PreferenceKeys.USER_EMAIL, "")
        val authProvider = prefs.getString(PreferenceKeys.AUTH_PROVIDER, "")
        val token = prefs.getString(PreferenceKeys.AUTH_TOKEN, "")
        val expiresAt = prefs.getLong(PreferenceKeys.TOKEN_EXPIRES_AT, 0)

        // Check if we have valid session data
        if (userId.isNotEmpty() && token.isNotEmpty() && userEmail.isNotEmpty()) {
            val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            // Check if token is not expired
            if (currentTime < expiresAt) {
                _currentUser.value =
                    AuthUser(
                        id = userId,
                        email = userEmail,
                        authProvider = authProvider.ifEmpty { "email" },
                    )
                _authToken.value = token
                _tokenExpiresAt.value = expiresAt

                // Set token in ApiClient
                com.kashyap.wizlyai.network.ApiClient.setAuthToken(token)
                return true
            } else {
                // Token expired, clear session
                clearSession()
            }
        }
        return false
    }

    /**
     * Check if token needs refresh (refresh when < 1 day remaining)
     */
    fun needsTokenRefresh(): Boolean {
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val timeRemaining = _tokenExpiresAt.value - currentTime
        val oneDayInMillis = 24 * 60 * 60 * 1000
        return timeRemaining > 0 && timeRemaining < oneDayInMillis
    }

    /**
     * Check if token is expired
     */
    fun isTokenExpired(): Boolean {
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return currentTime >= _tokenExpiresAt.value
    }

    /**
     * Update auth token (for refresh)
     */
    fun updateToken(
        newToken: String,
        newExpiresAt: Long,
    ) {
        _authToken.value = newToken
        _tokenExpiresAt.value = newExpiresAt

        // Persist updates
        preferencesManager?.apply {
            putString(PreferenceKeys.AUTH_TOKEN, newToken)
            putLong(PreferenceKeys.TOKEN_EXPIRES_AT, newExpiresAt)
        }

        // Update ApiClient
        com.kashyap.wizlyai.network.ApiClient.setAuthToken(newToken)
    }

    /**
     * Update user information
     */
    fun updateUser(user: AuthUser) {
        _currentUser.value = user

        // Persist updates
        preferencesManager?.apply {
            putString(PreferenceKeys.USER_ID, user.id)
            putString(PreferenceKeys.USER_EMAIL, user.email)
            putString(PreferenceKeys.AUTH_PROVIDER, user.authProvider)
        }
    }

    /**
     * Clear session and log out
     */
    fun clearSession() {
        _currentUser.value = null
        _authToken.value = null
        _tokenExpiresAt.value = 0

        // Clear cached data
        _cachedUserProfile.value = null
        cacheTimestamp = 0

        _studentProfile.value = StudentProfile()
        _needsOnboarding.value = false

        // Clear persisted data
        preferencesManager?.apply {
            remove(PreferenceKeys.STUDENT_NEEDS_ONBOARDING)
            remove(PreferenceKeys.USER_ID)
            remove(PreferenceKeys.USER_EMAIL)
            remove(PreferenceKeys.AUTH_PROVIDER)
            remove(PreferenceKeys.AUTH_TOKEN)
            remove(PreferenceKeys.TOKEN_EXPIRES_AT)
        }

        // Clear from ApiClient
        com.kashyap.wizlyai.network.ApiClient.clearAuthToken()
    }

    /**
     * Update the cached profile (e.g. after the student edits their name)
     */
    fun updateCachedUser(user: User) {
        _cachedUserProfile.value = user
    }

    /**
     * Invalidate cache (force refresh on next access)
     */
    fun invalidateCache() {
        cacheTimestamp = 0
    }

    /**
     * Get time until token expires (in milliseconds)
     */
    fun getTimeUntilExpiration(): Long {
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return _tokenExpiresAt.value - currentTime
    }

    /**
     * Get time until token expires (human readable)
     */
    fun getTimeUntilExpirationFormatted(): String {
        val timeRemaining = getTimeUntilExpiration()
        if (timeRemaining <= 0) return "Expired"

        val days = timeRemaining / (24 * 60 * 60 * 1000)
        val hours = (timeRemaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            else -> "Less than 1 hour"
        }
    }

    /**
     * Mark that user has seen onboarding
     */
    fun setHasSeenOnboarding(seen: Boolean) {
        preferencesManager?.putBoolean(PreferenceKeys.HAS_SEEN_ONBOARDING, seen)
    }

    /**
     * Check if user has seen onboarding
     */
    fun hasSeenOnboarding(): Boolean {
        return preferencesManager?.getBoolean(PreferenceKeys.HAS_SEEN_ONBOARDING, false) ?: false
    }
}
