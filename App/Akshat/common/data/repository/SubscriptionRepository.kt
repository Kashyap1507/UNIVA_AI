package com.kashyap.wizlyai.data.repository

import com.kashyap.wizlyai.network.SubscriptionService
import com.kashyap.wizlyai.network.models.CurrentSubscriptionResponse
import com.kashyap.wizlyai.network.models.SubscriptionPlansResponse
import com.kashyap.wizlyai.network.models.SubscriptionUpgradeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Repository for managing subscription data and state
 * Provides reactive state updates and caching for subscription info
 */
class SubscriptionRepository(
    private val subscriptionService: SubscriptionService = SubscriptionService()
) {
    // State for subscription plans
    private val _plansState = MutableStateFlow<SubscriptionPlansState>(SubscriptionPlansState.Loading)
    val plansState: StateFlow<SubscriptionPlansState> = _plansState.asStateFlow()

    // State for current user subscription
    private val _currentSubscriptionState = MutableStateFlow<CurrentSubscriptionState>(CurrentSubscriptionState.Loading)
    val currentSubscriptionState: StateFlow<CurrentSubscriptionState> = _currentSubscriptionState.asStateFlow()

    // Cached plans data
    private var cachedPlans: SubscriptionPlansResponse? = null
    private var plansCacheTime: Long = 0L
    private val plansCacheDuration = 5 * 60 * 1000L // 5 minutes

    // Cached subscription data
    private var cachedSubscription: CurrentSubscriptionResponse? = null
    private var subscriptionCacheTime: Long = 0L
    private val subscriptionCacheDuration = 1 * 60 * 1000L // 1 minute (refresh more frequently for usage stats)

    /**
     * Fetch all subscription plans
     * Uses cache if available and not expired
     *
     * @param forceRefresh Force refresh from server, ignoring cache
     */
    suspend fun fetchPlans(forceRefresh: Boolean = false) {
        // Check cache first
        if (!forceRefresh && cachedPlans != null && isCacheValid(plansCacheTime, plansCacheDuration)) {
            _plansState.value = SubscriptionPlansState.Success(cachedPlans!!)
            return
        }

        _plansState.value = SubscriptionPlansState.Loading

        val result = subscriptionService.getPlans()
        result.fold(
            onSuccess = { plansResponse ->
                cachedPlans = plansResponse
                plansCacheTime = Clock.System.now().toEpochMilliseconds()
                _plansState.value = SubscriptionPlansState.Success(plansResponse)
            },
            onFailure = { error ->
                _plansState.value = SubscriptionPlansState.Error(error.message ?: "Failed to load plans")
            }
        )
    }

    /**
     * Fetch current user's subscription and usage stats
     * Uses cache if available and not expired
     *
     * @param forceRefresh Force refresh from server, ignoring cache
     */
    suspend fun fetchCurrentSubscription(forceRefresh: Boolean = false) {
        // Check cache first
        if (!forceRefresh && cachedSubscription != null && isCacheValid(subscriptionCacheTime, subscriptionCacheDuration)) {
            _currentSubscriptionState.value = CurrentSubscriptionState.Success(cachedSubscription!!)
            return
        }

        _currentSubscriptionState.value = CurrentSubscriptionState.Loading

        val result = subscriptionService.getCurrentSubscription()
        result.fold(
            onSuccess = { subscriptionResponse ->
                cachedSubscription = subscriptionResponse
                subscriptionCacheTime = Clock.System.now().toEpochMilliseconds()
                _currentSubscriptionState.value = CurrentSubscriptionState.Success(subscriptionResponse)
            },
            onFailure = { error ->
                _currentSubscriptionState.value = CurrentSubscriptionState.Error(error.message ?: "Failed to load subscription")
            }
        )
    }

    /**
     * Upgrade user's subscription to a new tier
     *
     * @param tier Target subscription tier (free, basic, premium, pro)
     * @param autoRenew Whether to enable auto-renewal
     * @return Result with upgrade response or error
     */
    suspend fun upgradePlan(tier: String, autoRenew: Boolean = true): Result<SubscriptionUpgradeResponse> {
        val result = subscriptionService.upgradePlan(tier, autoRenew)

        // Invalidate caches on successful upgrade
        result.onSuccess {
            invalidateCache()
            // Refresh current subscription to get updated data
            fetchCurrentSubscription(forceRefresh = true)
        }

        return result
    }

    /**
     * Check if user can access a specific feature
     *
     * @param featureName Feature to check (work_checks, practice_sets, flashcard_sets)
     * @return True if feature is available, false if limit reached or error
     */
    fun canAccessFeature(featureName: String): Boolean {
        val currentState = _currentSubscriptionState.value
        return if (currentState is CurrentSubscriptionState.Success) {
            subscriptionService.canAccessFeature(featureName, currentState.data)
        } else {
            false // Default to blocking if we don't have subscription data
        }
    }

    /**
     * Check if should show upgrade prompt for a feature
     * Shows prompt when usage is at 80% or higher
     *
     * @param featureName Feature to check
     * @return True if should show prompt
     */
    fun shouldShowUpgradePrompt(featureName: String): Boolean {
        val currentState = _currentSubscriptionState.value
        return if (currentState is CurrentSubscriptionState.Success) {
            subscriptionService.shouldShowUpgradePrompt(featureName, currentState.data)
        } else {
            false
        }
    }

    /**
     * Get time remaining until usage reset
     *
     * @return Map with "hours" and "minutes" keys
     */
    fun getTimeUntilReset(): Map<String, Int> {
        val currentState = _currentSubscriptionState.value
        return if (currentState is CurrentSubscriptionState.Success) {
            val resetTime = currentState.data.usage.lastReset
            subscriptionService.calculateTimeUntilReset(resetTime)
        } else {
            mapOf("hours" to 0, "minutes" to 0)
        }
    }

    /**
     * Get current subscription data synchronously
     * Returns null if data not loaded yet
     */
    fun getCurrentSubscriptionSync(): CurrentSubscriptionResponse? {
        val currentState = _currentSubscriptionState.value
        return if (currentState is CurrentSubscriptionState.Success) {
            currentState.data
        } else {
            null
        }
    }

    /**
     * Invalidate all caches
     * Forces fresh data fetch on next request
     */
    fun invalidateCache() {
        cachedPlans = null
        plansCacheTime = 0L
        cachedSubscription = null
        subscriptionCacheTime = 0L
    }

    /**
     * Check if cache is still valid based on timestamp
     */
    private fun isCacheValid(cacheTime: Long, cacheDuration: Long): Boolean {
        return (Clock.System.now().toEpochMilliseconds() - cacheTime) < cacheDuration
    }

    /**
     * Reset repository state (useful for logout)
     */
    fun reset() {
        _plansState.value = SubscriptionPlansState.Loading
        _currentSubscriptionState.value = CurrentSubscriptionState.Loading
        invalidateCache()
    }
}

/**
 * State for subscription plans
 */
sealed class SubscriptionPlansState {
    object Loading : SubscriptionPlansState()
    data class Success(val data: SubscriptionPlansResponse) : SubscriptionPlansState()
    data class Error(val message: String) : SubscriptionPlansState()
}

/**
 * State for current subscription
 */
sealed class CurrentSubscriptionState {
    object Loading : CurrentSubscriptionState()
    data class Success(val data: CurrentSubscriptionResponse) : CurrentSubscriptionState()
    data class Error(val message: String) : CurrentSubscriptionState()
}
