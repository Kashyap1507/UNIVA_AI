package com.kashyap.wizlyai.util

import androidx.compose.runtime.Composable

/**
 * Platform-agnostic haptic feedback utility
 */
expect class HapticFeedback {
    /**
     * Perform light haptic feedback (for clicks, taps)
     */
    fun performLight()

    /**
     * Perform medium haptic feedback (for selections, toggles)
     */
    fun performMedium()

    /**
     * Perform heavy haptic feedback (for long-press, important actions)
     */
    fun performHeavy()

    /**
     * Perform success haptic feedback (for successful operations like edge detection)
     */
    fun performSuccess()
}

/**
 * Remember haptic feedback instance
 */
@Composable
expect fun rememberHapticFeedback(): HapticFeedback
