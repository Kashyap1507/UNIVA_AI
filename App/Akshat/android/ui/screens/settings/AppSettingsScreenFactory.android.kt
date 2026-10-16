package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.kashyap.wizlyai.data.repository.createAppSettingsRepository
import com.kashyap.wizlyai.ui.viewmodel.AppSettingsViewModel

/**
 * Android implementation of AppSettingsScreen with proper Context handling
 */
@Composable
actual fun AppSettingsScreenWrapper(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel =
        remember {
            AppSettingsViewModel(
                repository = createAppSettingsRepository(context),
                viewModelScope = coroutineScope,
            )
        }

    AppSettingsScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
    )
}
