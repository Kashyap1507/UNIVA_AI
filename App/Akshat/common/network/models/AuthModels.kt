package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Request Models
@Serializable
data class SignupRequest(
    @SerialName("email") val email: String,
    @SerialName("referral_code") val referralCode: String? = null,
)

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
)

@Serializable
data class VerifyEmailRequest(
    @SerialName("email") val email: String,
    @SerialName("code") val code: String,
    @SerialName("action") val action: String, // "signup" or "login"
    @SerialName("referral_code") val referralCode: String? = null,
)

@Serializable
data class ProviderAuthRequest(
    @SerialName("firebase_token") val firebaseToken: String,
    @SerialName("provider") val provider: String = "google", // "google", "facebook", "apple"
    @SerialName("referral_code") val referralCode: String? = null,
)

// Response Models
@Serializable
data class SignupResponse(
    @SerialName("message") val message: String,
    @SerialName("requires_verification") val requiresVerification: Boolean,
    @SerialName("email") val email: String,
)

@Serializable
data class LoginResponse(
    @SerialName("message") val message: String,
    @SerialName("requires_verification") val requiresVerification: Boolean,
    @SerialName("email") val email: String,
)

@Serializable
data class AuthUser(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("auth_provider") val authProvider: String,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("name") val name: String? = null,
    @SerialName("auth_provider") val authProvider: String,
    @SerialName("provider_uid") val providerUid: String? = null,
    @SerialName("verified") val verified: Boolean,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    // Referral system fields
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("referred_by_user_id") val referredByUserId: String? = null,
    @SerialName("referred_by_code") val referredByCode: String? = null,
    @SerialName("referral_limit") val referralLimit: Int = 5,
    @SerialName("referrals_remaining") val referralsRemaining: Int = 5,
    @SerialName("account_type") val accountType: String = "full", // "full" or "referral_limited"
)

@Serializable
data class VerifyEmailResponse(
    @SerialName("message") val message: String,
    @SerialName("token") val token: String? = null,
    @SerialName("user") val user: AuthUser? = null,
    @SerialName("bootstrap_data") val bootstrapData: BootstrapData? = null,
    @SerialName("user_profile") val userProfile: UserProfile? = null,
    @SerialName("student_profile") val studentProfile: StudentProfile? = null,
    @SerialName("needs_onboarding") val needsOnboarding: Boolean = false,
)

@Serializable
data class ProviderAuthResponse(
    @SerialName("message") val message: String,
    @SerialName("token") val token: String? = null,
    @SerialName("user") val user: AuthUser? = null,
    @SerialName("is_new_user") val isNewUser: Boolean = false,
    @SerialName("bootstrap_data") val bootstrapData: BootstrapData? = null,
    @SerialName("user_profile") val userProfile: UserProfile? = null,
    @SerialName("student_profile") val studentProfile: StudentProfile? = null,
    @SerialName("needs_onboarding") val needsOnboarding: Boolean = false,
)

// Error Response Model
@Serializable
data class AuthErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("message") val message: String? = null,
    @SerialName("details") val details: String? = null,
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
)

// Bootstrap Data Models (for initial app setup and sync)
@Serializable
data class BootstrapData(
    @SerialName("sync_timestamp") val syncTimestamp: String,
    @SerialName("app_settings") val appSettings: AppSettings? = null,
    // Subscription and usage data
    @SerialName("subscription") val subscription: com.kashyap.wizlyai.network.models.SubscriptionData? = null,
    // University student fields
    @SerialName("student_profile") val studentProfile: StudentProfile? = null,
    @SerialName("needs_onboarding") val needsOnboarding: Boolean = false,
)

/**
 * App Settings model for user preferences
 *
 * Synced via BootstrapData after authentication.
 * Settings are persisted in Room database and backend Firestore.
 *
 * Features:
 * - Cloud Sync toggle for document image uploads
 * - Cellular Data Sync for mobile data usage control
 * - Scan Quality presets for document scanning
 * - Cross-device sync for consistent user experience
 */
@Serializable
data class AppSettings(
    @SerialName("cloud_sync_enabled") val cloudSyncEnabled: Boolean = true, // Default: ON for cloud storage
    @SerialName("cellular_sync_enabled") val cellularSyncEnabled: Boolean = false,
    @SerialName("scan_quality") val scanQuality: String = "LARGE",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
