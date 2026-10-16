package com.kashyap.wizlyai.ui.util

import androidx.compose.runtime.Composable

/**
 * iOS implementation of PlatformBackHandler.
 *
 * iOS handles swipe-back gestures natively through UINavigationController/SwiftUI NavigationView.
 * This is a no-op implementation since iOS's native navigation handles back gestures automatically.
 *
 * If custom behavior is needed, this can be extended to call into Swift/Objective-C via
 * platform-specific bridges.
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS handles swipe-back gestures automatically through UINavigationController
    // No-op implementation - native iOS navigation handles this
    // If custom behavior needed, implement Swift bridge similar to AnalyticsTracker
}
