package com.kashyap.wizlyai.network

import com.kashyap.wizlyai.data.preferences.PreferenceKeys
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.network.NetworkConstants.BaseUrls.ANDROID_DEVICE

/**
 * Platform-specific function to get base URL
 * iOS implementation will use localhost for simulator, machine IP for device
 * Android implementation will use 10.0.2.2 for emulator, machine IP for device
 */
internal expect fun getPlatformSpecificBaseUrl(): String

/**
 * Platform-specific function to detect debug/development mode
 * This should return true only in debug builds, false in release
 */
internal expect fun getPlatformDebugFlag(): Boolean

/**
 * Network constants for the Wizly AI API
 */
object NetworkConstants {
    // PreferencesManager instance for reading server configuration
    private var preferencesManager: PreferencesManager? = null

    /**
     * Initialize NetworkConstants with PreferencesManager
     * Call this from MainActivity on app startup
     */
    fun initialize(prefs: PreferencesManager) {
        preferencesManager = prefs
    }

    // Different base URLs for different environments
    object BaseUrls {
        // For Android Emulator - use special IP that maps to host localhost
        const val ANDROID_EMULATOR = "10.0.2.2:5000"

        // For physical Android device on same network - use your machine's IP
        const val ANDROID_DEVICE = "192.168.0.78:5000"

        // For iOS Simulator - can use localhost
        const val IOS_SIMULATOR = "localhost:5000"

        // For physical iOS device - use your machine's IP
        const val IOS_DEVICE = "192.168.0.78:5000"

        // Production URL (HTTPS enforced) - Cloud Run deployment
        // TODO: Replace with your own Cloud Run host once the backend is deployed
        const val PRODUCTION = "YOUR-NEW-SERVER.run.app"
    }

    // Security: Protocol selection based on build type
    object Protocols {
        const val HTTPS = "https" // Production
        const val HTTP = "http" // Development only
    }

    /**
     * Default for the "use production server" toggle when the user hasn't set it:
     * debug builds use the local server, release builds use production
     */
    fun defaultUseProductionServer(): Boolean = !isDebugMode()

    private fun useProductionServer(): Boolean {
        val default = defaultUseProductionServer()
        return preferencesManager?.getBoolean(
            PreferenceKeys.USE_PRODUCTION_SERVER,
            defaultValue = default
        ) ?: default
    }

    /**
     * Get the appropriate base URL based on platform and environment
     * Checks user preference for production vs custom server
     */
    fun getBaseUrl(): String {
        // Check if user wants to use production server (from Developer Settings)
        val useProduction = useProductionServer()

        return when {
            // User chose production in dev settings
            useProduction -> BaseUrls.PRODUCTION
            // User chose custom server
            else -> getCustomServerIP()
        }
    }

    /**
     * Get custom server IP from preferences
     * Falls back to platform-specific default if not set
     */
    private fun getCustomServerIP(): String {
        val customIP = preferencesManager?.getString(
            PreferenceKeys.CUSTOM_SERVER_IP,
            defaultValue = ""
        ) ?: ""

        return if (customIP.isNotEmpty()) {
            customIP
        } else {
            // Fallback to platform-specific default
            getPlatformBaseUrl()
        }
    }

    /**
     * Get platform-specific base URL
     * This is implemented via expect/actual pattern in platform-specific source sets
     */
    private fun getPlatformBaseUrl(): String {
        // For iOS Simulator: use localhost
        // For iOS Device: use machine IP (192.168.0.78)
        // For Android Emulator: use 10.0.2.2
        // For Android Device: use machine IP (192.168.0.78)
        return try {
            getPlatformSpecificBaseUrl()
        } catch (e: Exception) {
            // Fallback to Android device if platform detection fails
            BaseUrls.ANDROID_DEVICE
        }
    }

    /**
     * Check if we're in debug mode
     * SECURITY: Uses platform-specific build configuration detection
     */
    fun isDebugMode(): Boolean {
        return try {
            getPlatformDebugFlag()
        } catch (e: Exception) {
            // Security: Default to production (false) if detection fails
            false
        }
    }

    /**
     * Get the appropriate protocol based on server selection
     * SECURITY: HTTPS is enforced for production server, HTTP only for local dev
     */
    fun getProtocol(): String {
        val useProduction = useProductionServer()

        return if (useProduction) {
            Protocols.HTTPS  // Always HTTPS for Cloud Run production server
        } else {
            Protocols.HTTP   // HTTP for local development server
        }
    }

    /**
     * Get full API URL with correct protocol
     * SECURITY: Automatically enforces HTTPS in production builds
     */
    fun getApiUrl(): String {
        val protocol = getProtocol()
        val baseUrl = getBaseUrl()
        val fullUrl = "$protocol://$baseUrl/api"

        val useProduction = useProductionServer()

        val customIP = preferencesManager?.getString(
            PreferenceKeys.CUSTOM_SERVER_IP,
            defaultValue = ""
        ) ?: ""

        // 🔍 DEBUG: Log computed API URL
        val serverMode = when {
            useProduction -> "PRODUCTION"
            customIP.isNotEmpty() -> "DEVELOPMENT (Custom: $customIP)"
            else -> "DEVELOPMENT (Default)"
        }

        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🌐 [WIZLY-NETWORK] API URL Configuration")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("📍 Protocol: $protocol")
        println("🖥️  Base URL: $baseUrl")
        println("🔗 Full API URL: $fullUrl")
        println("🏗️  Is Debug Mode: ${isDebugMode()}")
        println("🔧 Use Production Server: $useProduction")
        println("📡 Server Mode: $serverMode")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ [WIZLY-APP] Connected to: $fullUrl")

        return fullUrl
    }

    // Deprecated: Use getProtocol() instead for dynamic protocol selection
    @Deprecated(
        "Use getProtocol() for dynamic protocol selection",
        ReplaceWith("getProtocol()"),
    )
    const val PROTOCOL = "http"

    // Deprecated: Use getBaseUrl() instead for dynamic base URL selection
    @Deprecated(
        "Use getBaseUrl() for dynamic base URL selection",
        ReplaceWith("getBaseUrl()"),
    )
    const val BASE_URL = ANDROID_DEVICE

    // Deprecated: Use getApiUrl() instead
    @Deprecated(
        "Use getApiUrl() for dynamic protocol selection",
        ReplaceWith("getApiUrl()"),
    )
    const val FULL_BASE_URL = "http://$ANDROID_DEVICE/api"

    // Timeout Configuration
    const val CONNECT_TIMEOUT = 90_000L
    const val READ_TIMEOUT = 90_000L
    const val WRITE_TIMEOUT = 90_000L

    // Extended timeout for authentication operations (provider auth returns the bootstrap data)
    const val AUTH_OPERATION_TIMEOUT = 90_000L // 90 seconds for auth with bootstrap

    // Extended timeouts for AI-heavy operations (check my work, course material ingestion)
    // NOTE: 10 minutes, as multi-page scans and long documents can take a while to process.
    // Future optimization: Implement async job queue with polling to avoid long-lived connections.
    const val AI_OPERATION_TIMEOUT = 600_000L // 10 minutes for AI processing

    object Headers {
        const val CONTENT_TYPE = "Content-Type"
        const val ACCEPT = "Accept"
        const val AUTHORIZATION = "Authorization"
        const val USER_AGENT = "User-Agent"
        const val APPLICATION_JSON = "application/json"
        const val USER_AGENT_VALUE = "Wizly-Mobile-App/1.0"
        const val BEARER_PREFIX = "Bearer "
    }

    object Endpoints {
        // Authentication Endpoints
        const val AUTH_BASE = "/auth"
        const val SIGNUP = "$AUTH_BASE/signup"
        const val LOGIN = "$AUTH_BASE/login"
        const val VERIFY_EMAIL = "$AUTH_BASE/verify-email"
        const val LOGOUT = "$AUTH_BASE/logout"
        const val REFRESH_TOKEN = "$AUTH_BASE/refresh"

        // Scanned documents
        const val DOCUMENTS = "/documents"
        const val DOCUMENT_BY_ID = "/documents/{documentId}"
        const val DOCUMENT_DELETE = "/documents/{documentId}"

        // FCM Push Notification Endpoints
        const val FCM_TOKEN = "/fcm/token"
        const val FCM_TEST = "/fcm/test"

        // Global Endpoints
        const val HEALTH_CHECK = "/health"
    }

    object QueryParams {
        const val COURSE_ID = "course_id"
        const val DATE_FROM = "dateFrom"
        const val DATE_TO = "dateTo"
        const val LIMIT = "limit"
        const val OFFSET = "offset"
        const val SORT = "sort"
        const val ORDER = "order"
        const val TYPE = "type"
        const val SEARCH = "search"
    }

    object ErrorMessages {
        const val NETWORK_ERROR = "Network connection error"
        const val TIMEOUT_ERROR = "Request timed out"
        const val SERVER_ERROR = "Server error occurred"
        const val NO_INTERNET = "No internet connection"
        const val UNAUTHORIZED = "Authentication required"
        const val FORBIDDEN = "Access denied"
        const val NOT_FOUND = "Resource not found"
        const val VALIDATION_ERROR = "Invalid input data"
        const val UNKNOWN_ERROR = "An unexpected error occurred"
    }

    object StatusCodes {
        const val SUCCESS = 200
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
        const val VALIDATION_ERROR = 422
        const val SERVER_ERROR = 500
    }

    // Convenience constants for repositories
    const val LOGIN = Endpoints.LOGIN
    const val REGISTER = Endpoints.SIGNUP
    const val LOGOUT = Endpoints.LOGOUT
    const val REFRESH_TOKEN = Endpoints.REFRESH_TOKEN
    const val FCM_TOKEN = Endpoints.FCM_TOKEN
    const val FCM_TEST = Endpoints.FCM_TEST
}
