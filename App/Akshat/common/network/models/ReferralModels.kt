package com.kashyap.wizlyai.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Request Models
@Serializable
data class ValidateReferralRequest(
    @SerialName("referral_code") val referralCode: String
)

// Response Models
@Serializable
data class ValidateReferralResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("valid") val valid: Boolean,
    @SerialName("message") val message: String,
    @SerialName("referrer_info") val referrerInfo: ReferrerInfo? = null
)

@Serializable
data class ReferrerInfo(
    @SerialName("name") val name: String,
    @SerialName("referrals_remaining") val referralsRemaining: Int
)

@Serializable
data class MyReferralCodeResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("referral_code") val referralCode: String,
    @SerialName("referral_limit") val referralLimit: Int,
    @SerialName("referrals_remaining") val referralsRemaining: Int,
    @SerialName("can_refer") val canRefer: Boolean
)

@Serializable
data class ReferredUser(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("name") val name: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("account_type") val accountType: String
)

@Serializable
data class MyReferralsResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("referrals") val referrals: List<ReferredUser>,
    @SerialName("total_count") val totalCount: Int
)

@Serializable
data class ReferralStatsResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("referral_code") val referralCode: String,
    @SerialName("referral_limit") val referralLimit: Int,
    @SerialName("referrals_remaining") val referralsRemaining: Int,
    @SerialName("can_refer") val canRefer: Boolean,
    @SerialName("referral_stats") val referralStats: Map<String, Int>,
    @SerialName("referred_users") val referredUsers: List<ReferredUser>
)
