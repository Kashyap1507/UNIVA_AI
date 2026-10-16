package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.StudentProfileRepository
import com.kashyap.wizlyai.network.AuthService
import com.kashyap.wizlyai.network.models.*
import com.kashyap.wizlyai.sync.FcmManager
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for authentication operations
 * Implements email OTP-based authentication flow
 */
class AuthViewModel(
    private val authService: AuthService = AuthService(),
    private val studentProfileRepository: StudentProfileRepository = StudentProfileRepository(),
    private val viewModelScope: CoroutineScope,
) {
    // Authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Signup initiation state
    private val _signupState = MutableStateFlow<UiState<SignupResponse>>(UiState.Idle)
    val signupState: StateFlow<UiState<SignupResponse>> = _signupState.asStateFlow()

    // Login initiation state
    private val _loginState = MutableStateFlow<UiState<LoginResponse>>(UiState.Idle)
    val loginState: StateFlow<UiState<LoginResponse>> = _loginState.asStateFlow()

    // OTP verification state
    private val _verifyState = MutableStateFlow<UiState<VerifyEmailResponse>>(UiState.Idle)
    val verifyState: StateFlow<UiState<VerifyEmailResponse>> = _verifyState.asStateFlow()

    // Provider auth state
    private val _providerAuthState = MutableStateFlow<UiState<ProviderAuthResponse>>(UiState.Idle)
    val providerAuthState: StateFlow<UiState<ProviderAuthResponse>> = _providerAuthState.asStateFlow()

    // Current user
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    // Authentication token
    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    // True when the signed-in student still has to fill in their profile
    val needsOnboarding: StateFlow<Boolean> = com.kashyap.wizlyai.session.UserSessionManager.instance.needsOnboarding

    /**
     * Initiate signup process - sends OTP to email
     */
    fun initiateSignup(
        email: String,
        referralCode: String? = null,
    ) {
        viewModelScope.launch {
            println("🔍 AuthViewModel.initiateSignup - referralCode: $referralCode")
            _signupState.value = UiState.Loading
            _authState.value = AuthState.Loading

            val request =
                SignupRequest(
                    email = email,
                    referralCode = referralCode,
                )
            println("🔍 AuthViewModel - SignupRequest created with referralCode: ${request.referralCode}")

            val response = authService.initiateSignup(request)

            if (response.success && response.data != null) {
                _signupState.value = UiState.Success(response.data)
                _authState.value = AuthState.VerificationRequired(email, "signup")
            } else {
                val errorMessage = response.error ?: response.message ?: "Signup failed"
                _signupState.value = UiState.Error(errorMessage)
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    /**
     * Initiate login process - sends OTP to email
     */
    fun initiateLogin(email: String) {
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            _authState.value = AuthState.Loading

            val request = LoginRequest(email = email)

            val response = authService.initiateLogin(request)

            if (response.success && response.data != null) {
                _loginState.value = UiState.Success(response.data)
                _authState.value = AuthState.VerificationRequired(email, "login")
            } else {
                val errorMessage = response.error ?: response.message ?: "Login failed"
                _loginState.value = UiState.Error(errorMessage)
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    /**
     * Verify OTP code and complete authentication
     */
    fun verifyCode(
        email: String,
        code: String,
        action: String,
        referralCode: String? = null,
    ) {
        viewModelScope.launch {
            _verifyState.value = UiState.Loading
            _authState.value = AuthState.Loading

            val request =
                VerifyEmailRequest(
                    email = email,
                    code = code,
                    action = action,
                    referralCode = referralCode,
                )

            val response = authService.verifyEmail(request)

            if (response.success && response.data != null) {
                val data = response.data

                println("═══════════════════════════════════════════════════════")
                println("✅ EMAIL VERIFICATION SUCCESSFUL (AuthViewModel)")
                println("═══════════════════════════════════════════════════════")
                println("📧 Email: $email")
                println("🔑 Token received: ${data.token != null}")
                println("👤 User received: ${data.user != null}")
                println("")
                println("📦 BOOTSTRAP DATA CHECK:")
                if (data.bootstrapData != null) {
                    println("   ✅ Bootstrap data IS present!")
                    println("   📊 Sync timestamp: ${data.bootstrapData.syncTimestamp}")
                } else {
                    println("   ❌ Bootstrap data is NULL!")
                    println("   ⚠️  Backend did not return bootstrap_data in response")
                    println("   ⚠️  Settings sync will NOT happen!")
                }
                println("═══════════════════════════════════════════════════════")

                // Store token and user
                data.token?.let { token ->
                    _authToken.value = token
                    // Set JWT token in ApiClient for all future requests
                    com.kashyap.wizlyai.network.ApiClient.setAuthToken(token)

                    // Start user session
                    data.user?.let { user ->
                        println("🚀 Starting user session for: ${user.email} (ID: ${user.id})")
                        com.kashyap.wizlyai.session.UserSessionManager.instance.startSession(
                            user = user,
                            token = token,
                        )

                        // Cache the profile from the auth response (instant access)
                        com.kashyap.wizlyai.session.UserSessionManager.instance.cacheUserProfile(data.userProfile)
                    }
                }
                data.user?.let { _currentUser.value = it }
                com.kashyap.wizlyai.session.UserSessionManager.instance.updateStudentState(
                    profile = data.studentProfile,
                    needsOnboarding = data.needsOnboarding,
                )

                _verifyState.value = UiState.Success(data)
                _authState.value = AuthState.Authenticated
                println("═══════════════════════════════════════════════════════")

                // Register FCM token after successful authentication (non-blocking)
                try {
                    registerFcmToken()
                } catch (e: Exception) {
                    println("⚠️ FCM token registration failed (non-critical): ${e.message}")
                }

                // Sync bootstrap data using new SyncCoordinator (non-blocking)
                try {
                    data.bootstrapData?.let { bootstrapData ->
                        data.user?.let { user ->
                            println("🔄 Syncing bootstrap data with SyncCoordinator...")
                            com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.syncBootstrapData(bootstrapData, user)
                        } ?: println("⚠️ Cannot sync: user is null")
                    } ?: println("⚠️ No bootstrap data in auth response")
                } catch (e: Exception) {
                    println("⚠️ Bootstrap sync failed (non-critical): ${e.message}")
                    e.printStackTrace()
                }
            } else {
                val errorMessage = response.error ?: response.message ?: "Verification failed"
                _verifyState.value = UiState.Error(errorMessage)
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    /**
     * Set provider auth state to loading immediately when Google/Apple sign-in returns.
     * This provides instant UI feedback before waiting for Firebase token.
     */
    fun setProviderAuthLoading() {
        _providerAuthState.value = UiState.Loading
        _authState.value = AuthState.Loading
    }

    /**
     * Authenticate with social provider (Google, Facebook, Apple)
     */
    fun authenticateWithProvider(
        firebaseToken: String,
        provider: String,
        referralCode: String? = null,
    ) {
        viewModelScope.launch {
            _providerAuthState.value = UiState.Loading
            _authState.value = AuthState.Loading

            try {
                val request =
                    ProviderAuthRequest(
                        firebaseToken = firebaseToken,
                        provider = provider,
                        referralCode = referralCode,
                    )

                val response = authService.authenticateWithProvider(request)

                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🔐 [VIEWMODEL] Provider Auth Response")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("✅ Success: ${response.success}")
                println("📦 Has Data: ${response.data != null}")
                if (response.error != null) {
                    println("❌ Error Type: ${response.error}")
                    println("💬 Error Message: ${response.message}")
                }
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                if (response.success && response.data != null) {
                    val data = response.data
                    println("✅ Provider auth successful - User: ${data.user?.email}, Has token: ${data.token != null}")

                    println("═══════════════════════════════════════════════════════")
                    println("✅ PROVIDER AUTH SUCCESSFUL (AuthViewModel)")
                    println("═══════════════════════════════════════════════════════")
                    println("🔐 Provider: $provider")
                    println("🔑 Token received: ${data.token != null}")
                    println("👤 User received: ${data.user != null}")
                    println("🆕 Is new user: ${data.isNewUser}")
                    println("")
                    println("📦 BOOTSTRAP DATA CHECK:")
                    if (data.bootstrapData != null) {
                        println("   ✅ Bootstrap data IS present!")
                        println("   📊 Sync timestamp: ${data.bootstrapData.syncTimestamp}")
                    } else {
                        println("   ❌ Bootstrap data is NULL!")
                        println("   ⚠️  Backend did not return bootstrap_data in response")
                        println("   ⚠️  Settings sync will NOT happen!")
                    }
                    println("═══════════════════════════════════════════════════════")

                    // Store token and user
                    data.token?.let { token ->
                        println("💾 Storing auth token and setting up session...")
                        _authToken.value = token
                        // Set JWT token in ApiClient for all future requests
                        com.kashyap.wizlyai.network.ApiClient.setAuthToken(token)

                        // Start user session
                        data.user?.let { user ->
                            println("🚀 Starting user session for: ${user.email} (ID: ${user.id})")
                            com.kashyap.wizlyai.session.UserSessionManager.instance.startSession(
                                user = user,
                                token = token,
                            )

                            // Cache the profile from the auth response (instant access)
                            com.kashyap.wizlyai.session.UserSessionManager.instance.cacheUserProfile(data.userProfile)
                        }
                    }
                    data.user?.let { _currentUser.value = it }
                    com.kashyap.wizlyai.session.UserSessionManager.instance.updateStudentState(
                        profile = data.studentProfile,
                        needsOnboarding = data.needsOnboarding,
                    )

                    _providerAuthState.value = UiState.Success(data)
                    _authState.value = AuthState.Authenticated
                    println("═══════════════════════════════════════════════════════")

                    // Register FCM token after successful authentication (non-blocking)
                    try {
                        registerFcmToken()
                    } catch (e: Exception) {
                        println("⚠️ FCM token registration failed (non-critical): ${e.message}")
                    }

                    // Sync bootstrap data using new SyncCoordinator (non-blocking)
                    try {
                        data.bootstrapData?.let { bootstrapData ->
                            data.user?.let { user ->
                                println("🔄 Syncing bootstrap data with SyncCoordinator...")
                                com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.syncBootstrapData(bootstrapData, user)
                            } ?: println("⚠️ Cannot sync: user is null")
                        } ?: println("⚠️ No bootstrap data in auth response")
                    } catch (e: Exception) {
                        println("⚠️ Bootstrap sync failed (non-critical): ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    val errorMessage = response.error ?: response.message ?: "Provider auth failed"
                    println("❌ Provider auth error: $errorMessage")
                    _providerAuthState.value = UiState.Error(errorMessage)
                    _authState.value = AuthState.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = "Authentication error: ${e.message}"
                println("❌ Exception in authenticateWithProvider: $errorMessage")
                e.printStackTrace()
                _providerAuthState.value = UiState.Error(errorMessage)
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    /**
     * Logout current user
     */
    fun logout() {
        // Reset UI auth states right away so the login screen doesn't see a stale
        // Success and navigate back to home before the async cleanup below finishes
        _signupState.value = UiState.Idle
        _loginState.value = UiState.Idle
        _verifyState.value = UiState.Idle
        _providerAuthState.value = UiState.Idle

        viewModelScope.launch {
            // Unregister FCM token before logout
            unregisterFcmToken()

            // Send flashcard reviews answered offline while the token still works
            try {
                com.kashyap.wizlyai.data.repository.FlashcardRepository().syncPending()
            } catch (e: Exception) {
                println("⚠️ Failed to sync flashcard reviews: ${e.message}")
            }

            authService.logout()

            // Clear user session
            com.kashyap.wizlyai.session.UserSessionManager.instance.clearSession()

            // Clear cached courses and materials
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.kashyap.wizlyai.data.local.CourseCache.store.clear()
                    com.kashyap.wizlyai.data.local.LocalJson.store.clear()
                }
            } catch (e: Exception) {
                println("⚠️ Failed to clear course cache: ${e.message}")
            }

            // Clear bootstrap sync state (using new SyncCoordinator)
            try {
                com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.clearSyncData()
            } catch (e: Exception) {
                println("⚠️ Failed to clear sync data: ${e.message}")
            }

            // Clear local state
            _authToken.value = null
            _currentUser.value = null
            _authState.value = AuthState.Idle

            // Reset states
            _signupState.value = UiState.Idle
            _loginState.value = UiState.Idle
            _verifyState.value = UiState.Idle
            _providerAuthState.value = UiState.Idle
        }
    }

    /**
     * Register FCM token with backend after successful authentication
     * Called automatically after login/signup
     */
    private fun registerFcmToken() {
        viewModelScope.launch {
            try {
                val fcmToken = getFcmToken()
                if (fcmToken != null) {
                    println("📲 AuthViewModel: Registering FCM token")
                    val deviceId = getDeviceId()
                    val deviceName = com.kashyap.wizlyai.sync.getPlatformDeviceName()
                    val platform = com.kashyap.wizlyai.sync.getPlatformType()
                    val appVersion = com.kashyap.wizlyai.sync.getAppVersion()

                    FcmManager.instance.registerToken(
                        deviceId = deviceId,
                        token = fcmToken,
                        deviceName = deviceName,
                        platform = platform,
                        appVersion = appVersion,
                    ).fold(
                        onSuccess = {
                            println("✅ AuthViewModel: FCM token registered successfully")
                        },
                        onFailure = { error ->
                            println("❌ AuthViewModel: FCM token registration failed - ${error.message}")
                        },
                    )
                } else {
                    println("⚠️ AuthViewModel: FCM token not available yet")
                }
            } catch (e: Exception) {
                println("❌ AuthViewModel: Error registering FCM token - ${e.message}")
            }
        }
    }

    /**
     * Unregister FCM token from backend on logout
     */
    private fun unregisterFcmToken() {
        viewModelScope.launch {
            try {
                println("🗑️ AuthViewModel: Unregistering FCM token")
                FcmManager.instance.unregisterToken().fold(
                    onSuccess = {
                        println("✅ AuthViewModel: FCM token unregistered successfully")
                    },
                    onFailure = { error ->
                        println("❌ AuthViewModel: FCM token unregistration failed - ${error.message}")
                    },
                )
            } catch (e: Exception) {
                println("❌ AuthViewModel: Error unregistering FCM token - ${e.message}")
            }
        }
    }

    /**
     * Restore session from previous login
     */
    fun restoreSession(): Boolean {
        println("═══════════════════════════════════════════════════════")
        println("🔄 ATTEMPTING TO RESTORE SESSION")
        println("═══════════════════════════════════════════════════════")

        val restored = com.kashyap.wizlyai.session.UserSessionManager.instance.restoreSession()

        if (restored) {
            val user = com.kashyap.wizlyai.session.UserSessionManager.instance.currentUser.value
            val token = com.kashyap.wizlyai.session.UserSessionManager.instance.authToken.value

            println("✅ Session restored successfully!")
            println("   User ID: ${user?.id}")
            println("   User Email: ${user?.email}")
            println("   Token: ${token?.take(20)}...")

            _currentUser.value = user
            _authToken.value = token
            _authState.value = AuthState.Authenticated

            // Student fields: use the stored flag right away, then refresh from the server
            com.kashyap.wizlyai.session.UserSessionManager.instance.updateStudentState(
                profile = null,
                needsOnboarding = com.kashyap.wizlyai.session.UserSessionManager.instance.storedNeedsOnboarding(),
            )
            viewModelScope.launch { studentProfileRepository.getProfile() }

            // Re-register FCM token on session restore
            registerFcmToken()
        } else {
            println("❌ Session restoration failed - no valid session found")
        }

        println("═══════════════════════════════════════════════════════")
        return restored
    }

    /**
     * Reset states
     */
    fun resetSignupState() {
        _signupState.value = UiState.Idle
    }

    fun resetLoginState() {
        _loginState.value = UiState.Idle
    }

    fun resetVerifyState() {
        _verifyState.value = UiState.Idle
    }

    fun resetProviderAuthState() {
        _providerAuthState.value = UiState.Idle
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}

/**
 * Represents different authentication states
 */
sealed class AuthState {
    object Idle : AuthState()

    object Loading : AuthState()

    object Syncing : AuthState() // Syncing user data after authentication

    data class VerificationRequired(val email: String, val action: String) : AuthState()

    object Authenticated : AuthState()

    data class Error(val message: String) : AuthState()
}

/**
 * Platform-specific FCM token retrieval
 * Returns null if FCM token is not available yet
 */
expect fun getFcmToken(): String?

/**
 * Platform-specific unique device ID retrieval
 * Returns a unique identifier for this device
 */
expect fun getDeviceId(): String
