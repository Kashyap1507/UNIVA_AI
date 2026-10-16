package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.models.User
import com.kashyap.wizlyai.data.preferences.PreferenceKeys
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.data.preferences.getNotificationSettings
import com.kashyap.wizlyai.data.preferences.saveNotificationSettings
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Profile & Settings screen: the student's name and email
 * (from the session) and local notification preferences.
 */
class SettingsViewModel(
    private val preferencesManager: PreferencesManager? = null,
    @Suppress("unused") private val viewModelScope: CoroutineScope,
) {
    private val _userProfile = MutableStateFlow<UiState<User>>(currentUser()?.let { UiState.Success(it) } ?: UiState.Idle)
    val userProfile: StateFlow<UiState<User>> = _userProfile.asStateFlow()

    // Settings preferences
    private val _pushNotifications = MutableStateFlow(true)
    val pushNotifications: StateFlow<Boolean> = _pushNotifications.asStateFlow()

    private val _practiceReminders = MutableStateFlow(true)
    val practiceReminders: StateFlow<Boolean> = _practiceReminders.asStateFlow()

    /** Cached profile from the auth response, else the signed-in account. */
    private fun currentUser(): User? {
        val session = UserSessionManager.instance
        session.cachedUserProfile.value?.let { return it }
        val auth = session.currentUser.value ?: return null
        return User(id = auth.id, name = auth.name ?: auth.email.substringBefore("@"), email = auth.email)
    }

    /**
     * Load the profile shown at the top of the screen
     */
    fun loadUserProfile(@Suppress("UNUSED_PARAMETER") userId: String) {
        _userProfile.value =
            currentUser()?.let { UiState.Success(it) }
                ?: UiState.Error(message = "Couldn't load your profile")
    }

    /**
     * Load settings from preferences
     */
    fun loadSettings() {
        preferencesManager?.let { prefs ->
            _pushNotifications.value = prefs.getBoolean(PreferenceKeys.PUSH_NOTIFICATIONS, true)
            _practiceReminders.value = prefs.getBoolean(PreferenceKeys.PRACTICE_REMINDERS, true)
            SettingsStore.pushNotifications = _pushNotifications.value
            SettingsStore.practiceReminders = _practiceReminders.value
        } ?: run {
            // Fallback to in-memory store if PreferencesManager not available
            _pushNotifications.value = SettingsStore.pushNotifications
            _practiceReminders.value = SettingsStore.practiceReminders
        }
    }

    /**
     * Toggle push notifications
     * Also syncs with NotificationSettings.enableCalendarNotifications (deadline reminders)
     */
    fun togglePushNotifications() {
        _pushNotifications.value = !_pushNotifications.value
        SettingsStore.pushNotifications = _pushNotifications.value

        preferencesManager?.let { prefs ->
            prefs.putBoolean(PreferenceKeys.PUSH_NOTIFICATIONS, _pushNotifications.value)
            val currentSettings = prefs.getNotificationSettings()
            prefs.saveNotificationSettings(currentSettings.copy(enableCalendarNotifications = _pushNotifications.value))
        }
    }

    /**
     * Toggle study reminders
     */
    fun togglePracticeReminders() {
        _practiceReminders.value = !_practiceReminders.value
        SettingsStore.practiceReminders = _practiceReminders.value
        preferencesManager?.putBoolean(PreferenceKeys.PRACTICE_REMINDERS, _practiceReminders.value)
    }

    /**
     * Update notification settings
     */
    fun updateNotificationSettings(
        pushEnabled: Boolean,
        remindersEnabled: Boolean,
    ) {
        _pushNotifications.value = pushEnabled
        _practiceReminders.value = remindersEnabled
        SettingsStore.pushNotifications = pushEnabled
        SettingsStore.practiceReminders = remindersEnabled
        preferencesManager?.let { prefs ->
            prefs.putBoolean(PreferenceKeys.PUSH_NOTIFICATIONS, pushEnabled)
            prefs.putBoolean(PreferenceKeys.PRACTICE_REMINDERS, remindersEnabled)
        }
    }

    /**
     * Update email notification preferences
     */
    fun updateEmailNotifications(enabled: Boolean) {
        SettingsStore.emailNotifications = enabled
        preferencesManager?.putBoolean(PreferenceKeys.EMAIL_NOTIFICATIONS, enabled)
    }
}

/**
 * Simple in-memory settings store (mirrors PreferencesManager)
 */
object SettingsStore {
    var pushNotifications: Boolean = true
    var practiceReminders: Boolean = true
    var emailNotifications: Boolean = true
}
