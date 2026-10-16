package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.models.ScanQuality
import com.kashyap.wizlyai.data.repository.AppSettingsRepository
import com.kashyap.wizlyai.network.models.AppSettings
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.sync.BootstrapSyncCoordinator
import com.kashyap.wizlyai.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for App Settings screen
 *
 * Manages app-level user settings including cloud sync preference.
 * Settings are stored in Room database and synced to backend Firestore.
 *
 * Features:
 * - Reactive Flow-based state for UI observation
 * - Cloud Sync toggle with instant feedback
 * - Automatic backend sync on settings change
 * - Error handling with UiState wrapper
 *
 * Usage:
 * ```
 * val viewModel = remember { AppSettingsViewModel(context, coroutineScope) }
 * val cloudSyncEnabled by viewModel.cloudSyncEnabled.collectAsState()
 *
 * viewModel.loadSettings(userId)
 * viewModel.toggleCloudSync()
 * ```
 */
class AppSettingsViewModel(
    private val repository: AppSettingsRepository,
    private val viewModelScope: CoroutineScope,
) {
    // Settings state
    private val _settings = MutableStateFlow<UiState<AppSettings>>(UiState.Idle)
    val settings: StateFlow<UiState<AppSettings>> = _settings.asStateFlow()

    // Cloud sync enabled state (convenience for UI)
    private val _cloudSyncEnabled = MutableStateFlow(false)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    // Cellular sync enabled state (convenience for UI)
    private val _cellularSyncEnabled = MutableStateFlow(false)
    val cellularSyncEnabled: StateFlow<Boolean> = _cellularSyncEnabled.asStateFlow()

    // Scan quality state (convenience for UI)
    private val _scanQuality = MutableStateFlow(ScanQuality.LARGE)
    val scanQuality: StateFlow<ScanQuality> = _scanQuality.asStateFlow()

    // Loading state for toggle operations
    private val _isTogglingCloudSync = MutableStateFlow(false)
    val isTogglingCloudSync: StateFlow<Boolean> = _isTogglingCloudSync.asStateFlow()

    private val _isTogglingCellularSync = MutableStateFlow(false)
    val isTogglingCellularSync: StateFlow<Boolean> = _isTogglingCellularSync.asStateFlow()

    private val _isUpdatingScanQuality = MutableStateFlow(false)
    val isUpdatingScanQuality: StateFlow<Boolean> = _isUpdatingScanQuality.asStateFlow()

    // Sync Now state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncResult = MutableStateFlow<UiState<String>>(UiState.Idle)
    val syncResult: StateFlow<UiState<String>> = _syncResult.asStateFlow()

    /**
     * Load settings for current user
     *
     * Fetches settings from Room database and updates UI state.
     * Called when screen opens or user changes.
     *
     * @param userId User ID (defaults to current user)
     */
    fun loadSettings(userId: String? = null) {
        val targetUserId = userId ?: UserSessionManager.instance.userId ?: return

        viewModelScope.launch {
            _settings.value = UiState.Loading

            repository.getSettings(targetUserId).fold(
                onSuccess = { appSettings ->
                    _settings.value = UiState.Success(appSettings)
                    _cloudSyncEnabled.value = appSettings.cloudSyncEnabled
                    _cellularSyncEnabled.value = appSettings.cellularSyncEnabled
                    _scanQuality.value =
                        try {
                            ScanQuality.valueOf(appSettings.scanQuality)
                        } catch (e: Exception) {
                            ScanQuality.LARGE // Default fallback
                        }
                },
                onFailure = { error ->
                    _settings.value =
                        UiState.Error(
                            message = error.message ?: "Failed to load settings",
                            throwable = error,
                        )
                },
            )
        }
    }

    /**
     * Toggle cloud sync enabled setting
     *
     * Flips the current cloud sync state and saves to database.
     * Shows loading state during operation for UI feedback.
     *
     * Backend sync happens automatically via SyncRepository when marked as "pending".
     */
    fun toggleCloudSync() {
        val userId = UserSessionManager.instance.userId ?: return
        val newValue = !_cloudSyncEnabled.value

        viewModelScope.launch {
            _isTogglingCloudSync.value = true

            repository.updateCloudSync(userId, newValue).fold(
                onSuccess = { updatedSettings ->
                    _settings.value = UiState.Success(updatedSettings)
                    _cloudSyncEnabled.value = updatedSettings.cloudSyncEnabled

                    // Log state change
                    if (newValue) {
                        println("☁️ Cloud sync enabled - pending documents will upload automatically")
                        println("   Settings marked as 'pending' and will sync via background worker")
                    } else {
                        println("📱 Cloud sync disabled - new scans will stay local only")
                        println("   Images won't be uploaded to cloud storage")
                    }

                    // Trigger immediate backend sync to upload settings
                    println("🔄 Triggering backend sync for settings...")
                    BootstrapSyncCoordinator.syncAppSettings()
                },
                onFailure = { error ->
                    _settings.value =
                        UiState.Error(
                            message = error.message ?: "Failed to update cloud sync",
                            throwable = error,
                        )
                },
            )

            _isTogglingCloudSync.value = false
        }
    }

    /**
     * Set cloud sync enabled to specific value
     *
     * Direct setter (not toggle) for programmatic use.
     *
     * @param enabled New cloud sync state
     */
    fun setCloudSync(enabled: Boolean) {
        val userId = UserSessionManager.instance.userId ?: return

        // Only update if value changed
        if (_cloudSyncEnabled.value == enabled) return

        viewModelScope.launch {
            _isTogglingCloudSync.value = true

            repository.updateCloudSync(userId, enabled).fold(
                onSuccess = { updatedSettings ->
                    _settings.value = UiState.Success(updatedSettings)
                    _cloudSyncEnabled.value = updatedSettings.cloudSyncEnabled

                    // Trigger immediate backend sync to upload settings
                    println("🔄 Triggering backend sync for settings...")
                    BootstrapSyncCoordinator.syncAppSettings()
                },
                onFailure = { error ->
                    _settings.value =
                        UiState.Error(
                            message = error.message ?: "Failed to update cloud sync",
                            throwable = error,
                        )
                },
            )

            _isTogglingCloudSync.value = false
        }
    }

    /**
     * Observe settings changes reactively
     *
     * Subscribes to database Flow for real-time updates.
     * Updates will be received if settings change on another device
     * or from background sync.
     *
     * @param userId User ID (defaults to current user)
     */
    fun observeSettings(userId: String? = null) {
        val targetUserId = userId ?: UserSessionManager.instance.userId ?: return

        viewModelScope.launch {
            repository.getSettingsFlow(targetUserId).collect { appSettings ->
                if (appSettings != null) {
                    _settings.value = UiState.Success(appSettings)
                    _cloudSyncEnabled.value = appSettings.cloudSyncEnabled
                    _cellularSyncEnabled.value = appSettings.cellularSyncEnabled
                    _scanQuality.value =
                        try {
                            ScanQuality.valueOf(appSettings.scanQuality)
                        } catch (e: Exception) {
                            ScanQuality.LARGE // Default fallback
                        }
                }
            }
        }
    }

    /**
     * Toggle cellular sync enabled setting
     *
     * Controls whether files sync over cellular data or WiFi only.
     * Only applies when cloudSyncEnabled = true.
     */
    fun toggleCellularSync() {
        val userId = UserSessionManager.instance.userId ?: return
        val newValue = !_cellularSyncEnabled.value

        viewModelScope.launch {
            _isTogglingCellularSync.value = true

            repository.updateCellularSync(userId, newValue).fold(
                onSuccess = { updatedSettings ->
                    _settings.value = UiState.Success(updatedSettings)
                    _cellularSyncEnabled.value = updatedSettings.cellularSyncEnabled

                    // Log state change
                    if (newValue && _cloudSyncEnabled.value) {
                        println("📶 Cellular sync enabled - documents will upload over cellular data")
                        println("   Background sync will run on next opportunity")
                    } else if (!newValue) {
                        println("📵 Cellular sync disabled - will sync on WiFi only")
                    }

                    // Trigger immediate backend sync to upload settings
                    println("🔄 Triggering backend sync for settings...")
                    BootstrapSyncCoordinator.syncAppSettings()
                },
                onFailure = { error ->
                    _settings.value =
                        UiState.Error(
                            message = error.message ?: "Failed to update cellular sync",
                            throwable = error,
                        )
                },
            )

            _isTogglingCellularSync.value = false
        }
    }

    /**
     * Update scan quality setting
     *
     * Changes the resolution preset for document scanning.
     *
     * @param quality New scan quality preset
     */
    fun setScanQuality(quality: ScanQuality) {
        val userId = UserSessionManager.instance.userId ?: return

        // Only update if value changed
        if (_scanQuality.value == quality) return

        viewModelScope.launch {
            _isUpdatingScanQuality.value = true

            repository.updateScanQuality(userId, quality.name).fold(
                onSuccess = { updatedSettings ->
                    _settings.value = UiState.Success(updatedSettings)
                    _scanQuality.value =
                        try {
                            ScanQuality.valueOf(updatedSettings.scanQuality)
                        } catch (e: Exception) {
                            ScanQuality.LARGE // Default fallback
                        }

                    // Trigger immediate backend sync to upload settings
                    println("🔄 Triggering backend sync for settings...")
                    BootstrapSyncCoordinator.syncAppSettings()
                },
                onFailure = { error ->
                    _settings.value =
                        UiState.Error(
                            message = error.message ?: "Failed to update scan quality",
                            throwable = error,
                        )
                },
            )

            _isUpdatingScanQuality.value = false
        }
    }

    /**
     * Trigger immediate sync to backend
     *
     * Forces an immediate sync of all pending changes to the cloud.
     * If Cloud Sync is disabled, it will be enabled first.
     *
     * This method:
     * 1. Marks all pending settings as needing sync
     * 2. Calls BootstrapSyncCoordinator.smartSync(force = true)
     * 3. Updates sync result state for UI feedback
     *
     * Called when user taps "Sync Now" button.
     */
    fun triggerImmediateSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncResult.value = UiState.Loading

            try {
                // Trigger immediate sync via coordinator
                // This will upload all pending changes to backend
                BootstrapSyncCoordinator.smartSync(force = true)

                _syncResult.value = UiState.Success("Sync completed successfully")
            } catch (e: Exception) {
                _syncResult.value =
                    UiState.Error(
                        message = e.message ?: "Sync failed. Please try again.",
                        throwable = e,
                    )
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Reset sync result state
     *
     * Called after showing sync result to user (toast/snackbar).
     * Resets state to Idle so same result isn't shown again.
     */
    fun clearSyncResult() {
        _syncResult.value = UiState.Idle
    }

    /**
     * Clear settings
     *
     * Called during logout or account deletion.
     * Resets UI state to idle.
     */
    fun clearSettings() {
        _settings.value = UiState.Idle
        _cloudSyncEnabled.value = false
        _cellularSyncEnabled.value = false
        _scanQuality.value = ScanQuality.LARGE
        _isTogglingCloudSync.value = false
        _isTogglingCellularSync.value = false
        _isUpdatingScanQuality.value = false
        _isSyncing.value = false
        _syncResult.value = UiState.Idle
    }
}
