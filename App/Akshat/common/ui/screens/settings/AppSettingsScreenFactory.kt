package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.runtime.Composable

/**
 * Platform-specific factory for AppSettingsScreen
 * Android: Provides proper Context and ViewModel initialization
 * iOS: Shows placeholder or uses iOS-specific implementation
 */
@Composable
expect fun AppSettingsScreenWrapper(onNavigateBack: () -> Unit)
