package com.kashyap.wizlyai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kashyap.wizlyai.data.repository.SubscriptionRepository
import com.kashyap.wizlyai.network.models.CurrentSubscriptionResponse
import com.kashyap.wizlyai.network.models.FeatureUsage
import com.kashyap.wizlyai.network.models.SubscriptionPlan
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for subscription management
 * Handles subscription plans, usage tracking, and upgrades
 */
class SubscriptionViewModel : ViewModel() {
    private val repository = SubscriptionRepository()

    // State for subscription plans
    val plansState = repository.plansState
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            repository.plansState.value
        )

    // State for current subscription
    val currentSubscriptionState = repository.currentSubscriptionState
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            repository.currentSubscriptionState.value
        )

    // UI state for usage limit dialog
    private val _showLimitDialog = MutableStateFlow<String?>(null) // Feature name or null
    val showLimitDialog: StateFlow<String?> = _showLimitDialog.asStateFlow()

    // UI state for upgrade prompt
    private val _showUpgradePrompt = MutableStateFlow<String?>(null) // Feature name or null
    val showUpgradePrompt: StateFlow<String?> = _showUpgradePrompt.asStateFlow()

    // UI state for upgrade in progress
    private val _upgradeInProgress = MutableStateFlow(false)
    val upgradeInProgress: StateFlow<Boolean> = _upgradeInProgress.asStateFlow()

    // UI state for upgrade result
    private val _upgradeResult = MutableStateFlow<Result<String>?>(null)
    val upgradeResult: StateFlow<Result<String>?> = _upgradeResult.asStateFlow()

    /**
     * Load subscription plans from API
     */
    fun loadPlans(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.fetchPlans(forceRefresh)
        }
    }

    /**
     * Load current user's subscription and usage stats
     */
    fun loadCurrentSubscription(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.fetchCurrentSubscription(forceRefresh)
        }
    }

    /**
     * Check if user can access a feature
     * Shows limit dialog if limit reached
     * Shows upgrade prompt if approaching limit (80%+)
     *
     * @param featureName Feature to check (work_checks, practice_sets, flashcard_sets)
     * @return True if feature is available
     */
    fun canAccessFeature(featureName: String): Boolean {
        val canAccess = repository.canAccessFeature(featureName)

        if (!canAccess) {
            // Show limit dialog
            _showLimitDialog.value = featureName
            return false
        }

        // Check if should show upgrade prompt
        if (repository.shouldShowUpgradePrompt(featureName)) {
            _showUpgradePrompt.value = featureName
        }

        return true
    }

    /**
     * Upgrade to a new subscription tier
     *
     * @param tier Target tier (basic, premium, pro)
     * @param autoRenew Whether to enable auto-renewal
     */
    fun upgradePlan(tier: String, autoRenew: Boolean = true) {
        viewModelScope.launch {
            _upgradeInProgress.value = true
            _upgradeResult.value = null

            val result = repository.upgradePlan(tier, autoRenew)

            _upgradeInProgress.value = false
            _upgradeResult.value = result.map { response ->
                response.message
            }

            // Reload current subscription after upgrade
            if (result.isSuccess) {
                loadCurrentSubscription(forceRefresh = true)
            }
        }
    }

    /**
     * Get time remaining until usage reset
     * @return Map with "hours" and "minutes" keys
     */
    fun getTimeUntilReset(): Map<String, Int> {
        return repository.getTimeUntilReset()
    }

    /**
     * Get feature usage for a specific feature
     */
    fun getFeatureUsage(featureName: String): FeatureUsage? {
        val state = currentSubscriptionState.value
        return if (state is com.kashyap.wizlyai.data.repository.CurrentSubscriptionState.Success) {
            state.data.usage.features[featureName]
        } else {
            null
        }
    }

    /**
     * Get current subscription data
     */
    fun getCurrentSubscription(): CurrentSubscriptionResponse? {
        return repository.getCurrentSubscriptionSync()
    }

    /**
     * Dismiss limit reached dialog
     */
    fun dismissLimitDialog() {
        _showLimitDialog.value = null
    }

    /**
     * Dismiss upgrade prompt
     */
    fun dismissUpgradePrompt() {
        _showUpgradePrompt.value = null
    }

    /**
     * Clear upgrade result
     */
    fun clearUpgradeResult() {
        _upgradeResult.value = null
    }

    /**
     * Reset repository state (for logout)
     */
    fun reset() {
        repository.reset()
        _showLimitDialog.value = null
        _showUpgradePrompt.value = null
        _upgradeInProgress.value = false
        _upgradeResult.value = null
    }

    /**
     * Helper to get popular plan
     */
    fun getPopularPlan(): SubscriptionPlan? {
        val state = plansState.value
        return if (state is com.kashyap.wizlyai.data.repository.SubscriptionPlansState.Success) {
            state.data.plans.firstOrNull { it.isPopular }
        } else {
            null
        }
    }

    /**
     * Helper to check if user is on free tier
     */
    fun isFreeTier(): Boolean {
        val subscription = getCurrentSubscription()
        return subscription?.subscription?.tier == "free"
    }

    /**
     * Helper to check if user has unlimited access
     */
    fun hasUnlimitedAccess(): Boolean {
        val subscription = getCurrentSubscription()
        return subscription?.usage?.unlimited == true
    }
}
