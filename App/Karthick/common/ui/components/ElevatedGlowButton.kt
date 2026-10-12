package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ElevatedGlowButton - Modern button with elevation and subtle colored glow
 *
 * Inspired by modern iOS/Material You design principles with subtle, elegant effects.
 *
 * Features:
 * - White/light surface background (not colored)
 * - Elevated shadow for depth (matches reference design)
 * - Subtle glow effect using section color
 * - Scale animation on press (0.98)
 * - Rounded corners (14dp)
 * - Clean, minimalist aesthetic
 *
 * Design principles:
 * - Light surface with dark text (high contrast)
 * - Glow color customizable per section (purple for Study Collections, etc.)
 * - Elevation creates natural depth perception
 * - Subtle glow adds brand identity without overwhelming
 *
 * @param text Button text content
 * @param onClick Click handler
 * @param modifier Modifier for button customization
 * @param glowColor Color for the subtle glow effect (default: primary blue)
 * @param enabled Whether button is clickable
 * @param height Button height (default: 56dp)
 * @param cornerRadius Corner radius (default: 14dp)
 * @param elevation Shadow elevation (default: 6dp)
 * @param glowRadius Blur radius for glow effect (default: 16dp)
 */
@Composable
fun ElevatedGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
    elevation: Dp = 6.dp,
    glowRadius: Dp = 16.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = isSystemInDarkTheme()

    // Scale animation on press (matches existing buttons)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        label = "elevated_glow_button_scale",
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier =
            modifier
                .height(height)
                .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // Background glow layer (subtle, colored) - slightly inset to prevent edge bleeding
        if (enabled) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp) // Slight inset to prevent blur from showing at edges
                        .height(height - 4.dp) // Slight vertical inset
                        .background(
                            color = glowColor.copy(alpha = 0.3f),
                            shape = shape,
                        )
                        .blur(glowRadius),
            )
        }

        // Main button surface with elevation
        // In dark mode, shadows are not visible, so add a subtle border for separation
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null, // Scale animation instead of ripple
                    ) { onClick() },
            shape = shape,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            shadowElevation = if (enabled) elevation else 0.dp,
            border =
                if (isDarkTheme && enabled) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                } else {
                    null
                },
            contentColor =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                )
            }
        }
    }
}

/**
 * ElevatedGlowButton with leading icon
 *
 * Variant of ElevatedGlowButton that supports a leading icon (e.g., Add icon)
 *
 * @param text Button text content
 * @param onClick Click handler
 * @param icon Composable icon to display before text
 * @param modifier Modifier for button customization
 * @param glowColor Color for the subtle glow effect
 * @param enabled Whether button is clickable
 * @param height Button height (default: 56dp)
 * @param cornerRadius Corner radius (default: 14dp)
 * @param elevation Shadow elevation (default: 6dp)
 * @param glowRadius Blur radius for glow effect (default: 16dp)
 */
@Composable
fun ElevatedGlowButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
    elevation: Dp = 6.dp,
    glowRadius: Dp = 16.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = isSystemInDarkTheme()

    // Scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        label = "elevated_glow_button_icon_scale",
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier =
            modifier
                .height(height)
                .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // Background glow layer (subtle, colored) - slightly inset to prevent edge bleeding
        if (enabled) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp) // Slight inset to prevent blur from showing at edges
                        .height(height - 4.dp) // Slight vertical inset
                        .background(
                            color = glowColor.copy(alpha = 0.3f),
                            shape = shape,
                        )
                        .blur(glowRadius),
            )
        }

        // Main button surface with icon + text
        // In dark mode, shadows are not visible, so add a subtle border for separation
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onClick() },
            shape = shape,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            shadowElevation = if (enabled) elevation else 0.dp,
            border =
                if (isDarkTheme && enabled) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                } else {
                    null
                },
            contentColor =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Leading icon
                icon()

                Spacer(modifier = Modifier.width(8.dp))

                // Button text
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                )
            }
        }
    }
}
