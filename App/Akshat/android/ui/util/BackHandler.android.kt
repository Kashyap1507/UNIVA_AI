package com.kashyap.wizlyai.ui.util

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android implementation of PlatformBackHandler.
 *
 * Uses androidx.activity.compose.BackHandler to intercept:
 * - Hardware back button
 * - Gesture navigation (swipe from edge)
 * - Predictive back gesture (Android 13+)
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
