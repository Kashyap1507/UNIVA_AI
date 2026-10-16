package com.kashyap.wizlyai.network

import com.kashyap.wizlyai.network.models.CurrentSubscriptionResponse
import com.kashyap.wizlyai.network.models.SubscriptionPlansResponse
import com.kashyap.wizlyai.network.models.SubscriptionUpgradeRequest
import com.kashyap.wizlyai.network.models.SubscriptionUpgradeResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path

/**
 * Subscription Service
 * Handles all subscription-related API calls
 */
class SubscriptionService {

    /**
     * Get all available subscription plans
     *
     * @return Result with list of subscription plans
     */
    suspend fun getPlans(): Result<SubscriptionPlansResponse> {
        return try {
            val response = ApiClient.httpClient.get {
                url { path("/api/subscriptions/plans") }
            }
            Result.success(response.body<SubscriptionPlansResponse>())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get current user's subscription and usage stats
     * Requires authentication
     *
     * @return Result with current subscription details and usage
     */
    suspend fun getCurrentSubscription(): Result<CurrentSubscriptionResponse> {
        return try {
            val response = ApiClient.httpClient.get {
                url { path("/api/subscriptions/current") }
            }
            Result.success(response.body<CurrentSubscriptionResponse>())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upgrade user's subscription to a new tier
     * Requires authentication
     *
     * @param tier Target subscription tier (free, basic, premium, pro)
     * @param autoRenew Whether to auto-renew the subscription
     * @return Result with updated subscription details
     */
    suspend fun upgradePlan(tier: String, autoRenew: Boolean = true): Result<SubscriptionUpgradeResponse> {
        return try {
            val request = SubscriptionUpgradeRequest(tier = tier, autoRenew = autoRenew)
            val response = ApiClient.httpClient.post {
                url { path("/api/subscriptions/upgrade") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            Result.success(response.body<SubscriptionUpgradeResponse>())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user can access a specific feature
     * Based on current usage and limits
     *
     * @param featureName Feature to check (e.g., "work_checks")
     * @param currentUsage Current subscription response with usage data
     * @return True if feature is available, false if limit reached
     */
    fun canAccessFeature(featureName: String, currentUsage: CurrentSubscriptionResponse): Boolean {
        // If unlimited account, always allow
        if (currentUsage.usage.unlimited) {
            return true
        }

        // Check feature usage
        val featureUsage = currentUsage.usage.features[featureName] ?: return true
        return featureUsage.remaining > 0
    }

    /**
     * Check if user should see upgrade prompt
     * Show prompt when usage is at 80% or higher
     *
     * @param featureName Feature to check
     * @param currentUsage Current subscription response with usage data
     * @return True if should show prompt
     */
    fun shouldShowUpgradePrompt(featureName: String, currentUsage: CurrentSubscriptionResponse): Boolean {
        if (currentUsage.usage.unlimited) {
            return false
        }

        val featureUsage = currentUsage.usage.features[featureName] ?: return false
        return featureUsage.percentage >= 80 && featureUsage.remaining > 0
    }

    /**
     * Calculate time remaining until reset
     *
     * @param resetTime Reset time string (ISO format)
     * @return Map with hours and minutes remaining
     */
    fun calculateTimeUntilReset(resetTime: String?): Map<String, Int> {
        if (resetTime == null) {
            return mapOf("hours" to 0, "minutes" to 0)
        }

        try {
            // Parse reset time (format: "YYYY-MM-DD")
            // Calculate time until midnight of next day
            val now = kotlinx.datetime.Clock.System.now()
            val currentDate = now.toString().substring(0, 10)

            // If reset time is current date, calculate until midnight
            val hoursUntilMidnight = 24 - (now.toEpochMilliseconds() / 3600000 % 24).toInt()
            val minutesUntilMidnight = 60 - (now.toEpochMilliseconds() / 60000 % 60).toInt()

            return mapOf(
                "hours" to hoursUntilMidnight,
                "minutes" to minutesUntilMidnight
            )
        } catch (e: Exception) {
            return mapOf("hours" to 0, "minutes" to 0)
        }
    }

    companion object {
        // Feature name constants
        const val FEATURE_WORK_CHECKS = "work_checks"
        const val FEATURE_PRACTICE_SETS = "practice_sets"
        const val FEATURE_FLASHCARD_SETS = "flashcard_sets"
    }
}
