package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.theme.GradientBlueToTeal

/**
 * Primary button component - Neutral Theme
 *
 * Updated to use solid colors by default, matching minimalist design principles.
 * Gradient option preserved for critical CTAs only (use sparingly).
 *
 * Design:
 * - Solid color background (default: primary blue)
 * - Clean rounded corners (14dp)
 * - Subtle press animation (0.98 scale)
 * - 56dp height for comfortable tapping
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary, // Solid blue
    textColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 14.dp, // Slightly more rounded for modern look
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        label = "button_press_scale",
    )

    Box(
        modifier =
            modifier
                .height(height)
                .scale(scale)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (enabled) backgroundColor else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null, // Scale animation instead of ripple
                ) { onClick() }
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, // SemiBold for button text
            color = if (enabled) textColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

/**
 * GradientButton (Legacy - kept for critical CTAs)
 *
 * Use PrimaryButton for most cases. Reserve gradient for very important actions.
 */
@Deprecated(
    message = "Use PrimaryButton for most cases. Reserve gradient for critical CTAs only.",
    replaceWith = ReplaceWith("PrimaryButton(text, onClick, modifier, enabled = enabled)"),
)
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = GradientBlueToTeal,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        label = "button_press_scale",
    )

    Box(
        modifier =
            modifier
                .height(height)
                .scale(scale)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (enabled) gradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
        )
    }
}

/**
 * SecondaryButton
 *
 * Outlined button for secondary actions
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        label = "button_press_scale",
    )

    Box(
        modifier =
            modifier
                .height(height)
                .scale(scale)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) textColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
