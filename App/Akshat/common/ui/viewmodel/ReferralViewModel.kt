package com.kashyap.wizlyai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kashyap.wizlyai.network.ReferralService
import com.kashyap.wizlyai.network.models.MyReferralCodeResponse
import com.kashyap.wizlyai.network.models.ReferralStatsResponse
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.util.ClipboardManager
import com.kashyap.wizlyai.util.ShareManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Referral Screen (Invite Friends)
 * Manages referral code display, stats, and sharing functionality
 */
class ReferralViewModel : ViewModel() {
    private val referralService = ReferralService()

    // Referral code state
    private val _referralCodeState = MutableStateFlow<UiState<MyReferralCodeResponse>>(UiState.Idle)
    val referralCodeState: StateFlow<UiState<MyReferralCodeResponse>> = _referralCodeState.asStateFlow()

    // Referral stats state
    private val _referralStatsState = MutableStateFlow<UiState<ReferralStatsResponse>>(UiState.Idle)
    val referralStatsState: StateFlow<UiState<ReferralStatsResponse>> = _referralStatsState.asStateFlow()

    // Copy feedback state
    private val _copyFeedback = MutableStateFlow<String?>(null)
    val copyFeedback: StateFlow<String?> = _copyFeedback.asStateFlow()

    /**
     * Load user's referral code
     */
    fun loadReferralCode() {
        viewModelScope.launch {
            _referralCodeState.value = UiState.Loading

            val authToken = UserSessionManager.instance.authToken.value
            if (authToken == null) {
                _referralCodeState.value = UiState.Error("Not authenticated")
                return@launch
            }

            val response = referralService.getMyReferralCode(authToken)

            if (response.success && response.data != null) {
                _referralCodeState.value = UiState.Success(response.data)
            } else {
                _referralCodeState.value = UiState.Error(response.message ?: "Failed to load referral code")
            }
        }
    }

    /**
     * Load comprehensive referral statistics
     */
    fun loadReferralStats() {
        viewModelScope.launch {
            _referralStatsState.value = UiState.Loading

            val authToken = UserSessionManager.instance.authToken.value
            if (authToken == null) {
                _referralStatsState.value = UiState.Error("Not authenticated")
                return@launch
            }

            val response = referralService.getReferralStats(authToken)

            if (response.success && response.data != null) {
                _referralStatsState.value = UiState.Success(response.data)
            } else {
                _referralStatsState.value = UiState.Error(response.message ?: "Failed to load referral stats")
            }
        }
    }

    /**
     * Copy referral code to clipboard
     */
    fun copyReferralCode(code: String) {
        val success = ClipboardManager.copyToClipboard(code, "Univa AI Referral Code")
        if (success) {
            ClipboardManager.showCopiedMessage("Referral code copied!")
            _copyFeedback.value = "Copied!"

            // Clear feedback after 2 seconds
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                _copyFeedback.value = null
            }
        } else {
            _copyFeedback.value = "Failed to copy"
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                _copyFeedback.value = null
            }
        }
    }

    /**
     * Share referral code via native share dialog
     */
    fun shareReferralCode(code: String, userName: String? = null) {
        val message = ShareManager.getReferralShareMessage(code, userName)
        val success = ShareManager.shareText(message, "Invite Friends to Univa AI")

        if (!success) {
            _copyFeedback.value = "Failed to share"
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                _copyFeedback.value = null
            }
        }
    }

    /**
     * Refresh all referral data
     */
    fun refresh() {
        loadReferralCode()
        loadReferralStats()
    }

    /**
     * Clear copy feedback message
     */
    fun clearCopyFeedback() {
        _copyFeedback.value = null
    }
}
