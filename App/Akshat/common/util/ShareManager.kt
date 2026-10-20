package com.kashyap.wizlyai.util

/**
 * Platform-agnostic share manager for sharing text via native share dialog
 * Uses expect/actual pattern for platform-specific implementations
 */
expect object ShareManager {
    /**
     * Initialize the share manager with context (Android only)
     * No-op on iOS
     */
    fun initialize(context: Any)

    /**
     * Share text using native share sheet
     * @param text Text to share
     * @param subject Optional subject/title for the share (Android only)
     * @return true if share was initiated, false otherwise
     */
    fun shareText(text: String, subject: String = "Univa AI Referral Code"): Boolean

    /**
     * Get suggested share message for referral code
     * @param referralCode User's referral code
     * @param userName Optional user name to personalize message
     * @return Formatted share message
     */
    fun getReferralShareMessage(referralCode: String, userName: String? = null): String
}
