package com.kashyap.wizlyai.ui.util

import androidx.compose.runtime.Composable

/**
 * Platform-agnostic BackHandler for intercepting system back button/gesture.
 *
 * On Android: Uses androidx.activity.compose.BackHandler to intercept hardware/gesture back
 * On iOS: Integrates with SwiftUI navigation gestures (if needed)
 *
 * @param enabled Whether the back handler is enabled
 * @param onBack Callback when back is pressed/gestured
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
