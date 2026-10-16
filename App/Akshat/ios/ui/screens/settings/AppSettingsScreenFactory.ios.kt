package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.kashyap.wizlyai.data.repository.createAppSettingsRepository
import com.kashyap.wizlyai.ui.viewmodel.AppSettingsViewModel

/**
 * iOS implementation of AppSettingsScreen
 *
 * Note: Settings persistence uses placeholder implementation
 * TODO: Implement proper iOS persistence using NSUserDefaults or CoreData
 */
@Composable
actual fun AppSettingsScreenWrapper(onNavigateBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    val viewModel =
        remember {
            AppSettingsViewModel(
                repository = createAppSettingsRepository(),
                viewModelScope = coroutineScope,
            )
        }

    AppSettingsScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
    )
}
