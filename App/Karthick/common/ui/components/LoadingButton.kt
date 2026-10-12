package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.theme.TealPrimary

/**
 * Modern loading button with gradient fill and magical sparkle animation
 * Features:
 * - Blue-teal gradient fill animation (left to right sweep)
 * - Rotating sparkle icon (inspired by splash screen)
 * - Scale animation on press (0.98)
 * - Text always visible during loading
 * - Full-width gradient overlay
 *
 * Design principles:
 * - Magical, engaging aesthetic
 * - Consistent blue-teal gradient for all buttons
 * - Adaptive sparkle color (blue for white buttons, white for colored buttons)
 * - Clear loading state feedback
 */
@Composable
fun LoadingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    loadingText: String? = null,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    disabledBackgroundColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    height: Dp = 56.dp,
    cornerRadius: Dp = 12.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isActuallyEnabled = enabled && !isLoading

    // Scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isActuallyEnabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "button_scale",
    )

    // Content alpha for smooth transition
    val contentAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "content_alpha",
    )

    // Gradient fill animation (0 to 1 sweep from left to right)
    val infiniteTransition = rememberInfiniteTransition(label = "gradient_fill")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "gradient_offset",
    )

    // Sparkle rotation animation (continuous 360° rotation)
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "sparkle_rotation",
    )

    // Use blue-teal gradient fill for all buttons
    val loadingGradient =
        Brush.linearGradient(
            colors =
                listOf(
                    Color.Transparent,
                    BluePrimary.copy(alpha = 0.3f),
                    TealPrimary.copy(alpha = 0.5f),
                    BluePrimary.copy(alpha = 0.3f),
                    Color.Transparent,
                ),
            start = Offset(gradientOffset * 1000f, 0f),
            end = Offset(gradientOffset * 1000f + 500f, 0f),
        )

    // Use BluePrimary sparkle for white buttons, white for colored buttons
    val sparkleColor =
        if (backgroundColor == Color.White) {
            BluePrimary
        } else {
            Color.White
        }

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier =
            modifier
                .height(height)
                .scale(scale)
                .clip(shape)
                .then(
                    if (borderColor != null) {
                        Modifier.border(borderWidth, borderColor, shape)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    enabled = isActuallyEnabled,
                    interactionSource = interactionSource,
                    indication = null, // Remove default ripple, using scale instead
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // Background layer (static)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        if (isActuallyEnabled) {
                            backgroundColor
                        } else {
                            disabledBackgroundColor
                        },
                    ),
        )

        // Gradient fill overlay (full width, only visible when loading)
        if (isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(brush = loadingGradient),
            )
        }

        // Content row with sparkle (left) and text (center)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rotating sparkle icon on the left (only visible when loading)
            if (isLoading) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .rotate(sparkleRotation),
                    tint = sparkleColor,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Text content (shows loadingText when loading, otherwise normal text)
            Text(
                text = if (isLoading && loadingText != null) loadingText else text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isActuallyEnabled) contentColor else disabledContentColor,
            )
        }
    }
}

/**
 * GlowLoadingButton - Modern button with glow effect and loading animation
 *
 * Combines the glow effect from ElevatedGlowButton with LoadingButton's
 * gradient fill animation for a premium, iOS 26-inspired look.
 *
 * Features:
 * - Colored glow effect behind the button (blur layer)
 * - Scale animation on press (0.98)
 * - Gradient fill animation during loading
 * - Rotating sparkle icon during loading
 * - Works great on glassmorphism containers
 *
 * @param text Button text content
 * @param onClick Click handler
 * @param modifier Modifier for button customization
 * @param leadingIcon Optional leading icon composable (shown when not loading)
 * @param isLoading Whether button is in loading state
 * @param loadingText Text to show during loading (optional)
 * @param enabled Whether button is clickable
 * @param glowColor Color for the glow effect
 * @param glowRadius Blur radius for glow (default: 12dp)
 * @param glowAlpha Opacity of glow effect (default: 0.4f)
 * @param backgroundColor Button background color
 * @param contentColor Text/icon color
 * @param height Button height (default: 56dp)
 * @param cornerRadius Corner radius (default: 14dp)
 * @param borderColor Optional border color
 * @param borderWidth Border width (default: 1dp)
 */
@Composable
fun GlowLoadingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    isLoading: Boolean = false,
    loadingText: String? = null,
    enabled: Boolean = true,
    glowColor: Color = BluePrimary,
    glowRadius: Dp = 12.dp,
    glowAlpha: Float = 0.4f,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    disabledBackgroundColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    height: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isActuallyEnabled = enabled && !isLoading

    // Scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isActuallyEnabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "glow_button_scale",
    )

    // Glow intensity animation on press
    val currentGlowAlpha by animateFloatAsState(
        targetValue = if (isPressed && isActuallyEnabled) glowAlpha + 0.2f else glowAlpha,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "glow_alpha",
    )

    // Gradient fill animation (0 to 1 sweep from left to right)
    val infiniteTransition = rememberInfiniteTransition(label = "glow_gradient_fill")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "glow_gradient_offset",
    )

    // Sparkle rotation animation
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "glow_sparkle_rotation",
    )

    // Loading gradient overlay
    val loadingGradient =
        Brush.linearGradient(
            colors =
                listOf(
                    Color.Transparent,
                    BluePrimary.copy(alpha = 0.3f),
                    TealPrimary.copy(alpha = 0.5f),
                    BluePrimary.copy(alpha = 0.3f),
                    Color.Transparent,
                ),
            start = Offset(gradientOffset * 1000f, 0f),
            end = Offset(gradientOffset * 1000f + 500f, 0f),
        )

    // Sparkle color adapts to button background
    val sparkleColor =
        if (backgroundColor == Color.White || backgroundColor.alpha < 0.5f) {
            BluePrimary
        } else {
            Color.White
        }

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.Center,
    ) {
        // Glow layer (blurred background)
        if (isActuallyEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(height - 4.dp)
                    .background(
                        color = glowColor.copy(alpha = currentGlowAlpha),
                        shape = shape,
                    )
                    .blur(glowRadius),
            )
        }

        // Main button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .scale(scale)
                .clip(shape)
                .then(
                    if (borderColor != null) {
                        Modifier.border(borderWidth, borderColor, shape)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    enabled = isActuallyEnabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            // Background layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isActuallyEnabled) {
                            backgroundColor
                        } else {
                            disabledBackgroundColor
                        },
                    ),
            )

            // Gradient fill overlay (only visible when loading)
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = loadingGradient),
                )
            }

            // Content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rotating sparkle icon (only visible when loading)
                if (isLoading) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(sparkleRotation),
                        tint = sparkleColor,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (leadingIcon != null) {
                    // Leading icon (only visible when not loading)
                    leadingIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Text content
                Text(
                    text = if (isLoading && loadingText != null) loadingText else text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isActuallyEnabled) contentColor else disabledContentColor,
                )
            }
        }
    }
}
