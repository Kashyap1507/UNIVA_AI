package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subscription tier enum
 */
@Serializable
enum class SubscriptionTier {
    @SerialName("free") FREE,
    @SerialName("basic") BASIC,
    @SerialName("premium") PREMIUM,
    @SerialName("pro") PRO
}

/**
 * Subscription status enum
 */
@Serializable
enum class SubscriptionStatus {
    @SerialName("active") ACTIVE,
    @SerialName("expired") EXPIRED,
    @SerialName("cancelled") CANCELLED
}

/**
 * Subscription plan DTO
 * Represents a subscription tier with pricing and features
 */
@Serializable
data class SubscriptionPlan(
    @SerialName("id") val id: String,
    @SerialName("tier") val tier: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("price_monthly") val priceMonthly: Double,
    @SerialName("price_yearly") val priceYearly: Double,
    @SerialName("limits") val limits: Map<String, Int>,
    @SerialName("features") val features: List<String>,
    @SerialName("is_popular") val isPopular: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0
)

/**
 * Feature usage DTO
 * Tracks usage for a specific feature (work checks, practice sets, etc.)
 */
@Serializable
data class FeatureUsage(
    @SerialName("used") val used: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("remaining") val remaining: Int,
    @SerialName("percentage") val percentage: Int
)

/**
 * Usage summary DTO
 * Complete summary of user's feature usage
 */
@Serializable
data class UsageSummary(
    @SerialName("account_type") val accountType: String, // "full" or "referral_limited"
    @SerialName("unlimited") val unlimited: Boolean,
    @SerialName("features") val features: Map<String, FeatureUsage>,
    @SerialName("last_reset") val lastReset: String? = null
)

/**
 * Subscription data DTO
 * User's current subscription details and usage
 */
@Serializable
data class SubscriptionData(
    @SerialName("tier") val tier: String,
    @SerialName("status") val status: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("usage_summary") val usageSummary: UsageSummary,
    @SerialName("reset_time") val resetTime: String? = null
)

/**
 * Current subscription response
 * Full subscription details with plan info
 */
@Serializable
data class CurrentSubscriptionResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("subscription") val subscription: SubscriptionDetails,
    @SerialName("usage") val usage: UsageSummary
)

/**
 * Subscription details DTO
 * Extended subscription information
 */
@Serializable
data class SubscriptionDetails(
    @SerialName("tier") val tier: String,
    @SerialName("status") val status: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("auto_renew") val autoRenew: Boolean = false,
    @SerialName("plan_name") val planName: String,
    @SerialName("plan_limits") val planLimits: Map<String, Int>
)

/**
 * Subscription plans response
 */
@Serializable
data class SubscriptionPlansResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("plans") val plans: List<SubscriptionPlan>
)

/**
 * Subscription upgrade request
 */
@Serializable
data class SubscriptionUpgradeRequest(
    @SerialName("tier") val tier: String,
    @SerialName("auto_renew") val autoRenew: Boolean = true
)

/**
 * Subscription upgrade response
 */
@Serializable
data class SubscriptionUpgradeResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String,
    @SerialName("subscription") val subscription: SubscriptionDetails
)
