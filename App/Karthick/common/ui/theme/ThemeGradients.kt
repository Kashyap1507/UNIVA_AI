package com.kashyap.wizlyai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware gradient system for Wizly app.
 * These gradients automatically adapt to light/dark mode using MaterialTheme colors.
 *
 * Usage:
 * ```
 * Box(
 *     modifier = Modifier.background(rememberPrimaryGradient())
 * )
 * ```
 */

/**
 * Primary gradient - Blue to Teal
 * Used for: Main action buttons, hero sections, greeting cards
 */
@Composable
fun rememberPrimaryGradient(
    alpha: Float = 1f,
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, alpha) {
        Brush.linearGradient(
            colors =
                listOf(
                    colorScheme.primary.copy(alpha = alpha),
                    colorScheme.secondary.copy(alpha = alpha),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Subtle primary gradient with low opacity
 * Used for: Card backgrounds, icon backgrounds, subtle highlights
 */
@Composable
fun rememberSubtlePrimaryGradient(
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        Brush.linearGradient(
            colors =
                listOf(
                    colorScheme.primary.copy(alpha = 0.12f),
                    colorScheme.secondary.copy(alpha = 0.12f),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Secondary gradient - Teal to Primary
 * Used for: Alternative action buttons, secondary hero sections
 */
@Composable
fun rememberSecondaryGradient(
    alpha: Float = 1f,
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, alpha) {
        Brush.linearGradient(
            colors =
                listOf(
                    colorScheme.secondary.copy(alpha = alpha),
                    colorScheme.primary.copy(alpha = alpha),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Tertiary gradient - Purple tones
 * Used for: Special sections, premium features, tertiary actions
 */
@Composable
fun rememberTertiaryGradient(
    alpha: Float = 1f,
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, alpha) {
        Brush.linearGradient(
            colors =
                listOf(
                    colorScheme.tertiary.copy(alpha = alpha),
                    colorScheme.tertiaryContainer.copy(alpha = alpha * 0.7f),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Surface gradient with subtle elevation effect
 * Used for: Elevated cards, modal backgrounds
 */
@Composable
fun rememberSurfaceGradient(
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        Brush.linearGradient(
            colors =
                listOf(
                    colorScheme.surface,
                    colorScheme.surfaceVariant,
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Radial gradient for circular elements
 * Used for: Profile avatars, circular icons, spotlight effects
 */
@Composable
fun rememberPrimaryRadialGradient(
    alpha: Float = 1f,
    radius: Float = Float.POSITIVE_INFINITY,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, alpha, radius) {
        Brush.radialGradient(
            colors =
                listOf(
                    colorScheme.primary.copy(alpha = alpha),
                    colorScheme.secondary.copy(alpha = alpha * 0.7f),
                    Color.Transparent,
                ),
            radius = radius,
        )
    }
}

/**
 * Success gradient - Green tones
 * Used for: Success states, completion indicators, positive feedback
 */
@Composable
fun rememberSuccessGradient(
    alpha: Float = 1f,
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    return remember(colorScheme, alpha, isDark) {
        val successLight = Color(0xFF10B981)
        val successDark = Color(0xFF34D399)

        Brush.linearGradient(
            colors =
                listOf(
                    if (isDark) successDark.copy(alpha = alpha) else successLight.copy(alpha = alpha),
                    if (isDark) Color(0xFF059669).copy(alpha = alpha * 0.8f) else Color(0xFF059669).copy(alpha = alpha * 0.8f),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Warning gradient - Amber/Orange tones
 * Used for: Warning states, pending indicators, caution messages
 */
@Composable
fun rememberWarningGradient(
    alpha: Float = 1f,
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    return remember(colorScheme, alpha, isDark) {
        val warningLight = Color(0xFFF59E0B)
        val warningDark = Color(0xFFFBBF24)

        Brush.linearGradient(
            colors =
                listOf(
                    if (isDark) warningDark.copy(alpha = alpha) else warningLight.copy(alpha = alpha),
                    if (isDark) Color(0xFFD97706).copy(alpha = alpha * 0.8f) else Color(0xFFD97706).copy(alpha = alpha * 0.8f),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Error gradient - Red tones
 * Used for: Error states, destructive actions, critical alerts
 */
@Composable
fun rememberErrorGradient(
    alpha: Float = 1f,
    start: Offset = Offset(0f, 0f),
    end: Offset = Offset.Infinite,
): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, alpha) {
        Brush.linearGradient(
            colors =
                listOf(
                    colorScheme.error.copy(alpha = alpha),
                    colorScheme.errorContainer.copy(alpha = alpha * 0.8f),
                ),
            start = start,
            end = end,
        )
    }
}

/**
 * Dialog/BottomSheet surface gradient - unified styling
 * Used for: AlertDialogs, DropdownMenus, BottomSheets, Modal backgrounds
 *
 * Colors match the bottom sheet gradient:
 * - Light mode: #F7F7F7 → #EDEDED
 * - Dark mode: #1C1C1E → #141414
 */
@Composable
fun rememberDialogSurfaceGradient(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    return remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color(0xFF1C1C1E), // Dark gray at top
                    Color(0xFF141414), // Darker gray at bottom
                )
            } else {
                listOf(
                    BackgroundPrimary, // #F7F7F7 - same as home screen top
                    Color(0xFFEDEDED), // Silver grey at bottom
                )
            },
        )
    }
}

/**
 * Get dialog surface top color (solid, for containerColor parameters)
 * Used when a component needs a solid color but should match the dialog gradient
 */
@Composable
fun rememberDialogSurfaceColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    return remember(isDark) {
        if (isDark) Color(0xFF1C1C1E) else BackgroundPrimary
    }
}

/**
 * Extension function to calculate color luminance
 * Used internally to determine if we're in dark mode
 */
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
