package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassmorphismCard - iOS 26 style frosted glass container
 *
 * Features:
 * - Frosted glass effect in dark mode with semi-transparent background
 * - Subtle white border for depth and definition
 * - Background blur layer for authentic glassmorphism
 * - Falls back to standard white card in light mode
 * - Smooth corners and modern aesthetic
 *
 * Design inspired by iOS 26 glass design language:
 * - Semi-transparent surfaces that let background show through
 * - Subtle borders that catch light
 * - Blur effects for depth perception
 *
 * @param modifier Modifier for the card
 * @param cornerRadius Corner radius for the card shape (default: 24dp)
 * @param blurRadius Blur radius for glass effect (default: 20dp)
 * @param glassAlpha Transparency of the glass surface (default: 0.08f for dark, 1f for light)
 * @param borderAlpha Border opacity (default: 0.15f)
 * @param contentPadding Padding inside the card (default: 32dp)
 * @param content Card content
 */
@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 20.dp,
    glassAlpha: Float = 0.08f,
    borderAlpha: Float = 0.15f,
    contentPadding: PaddingValues = PaddingValues(32.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    if (isDarkTheme) {
        // Glassmorphism effect for dark mode - transparent with glass border
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
            ),
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    } else {
        // Standard white card for light mode
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

/**
 * GlassmorphismCard with custom fill width
 *
 * Convenience wrapper that fills max width by default
 */
@Composable
fun GlassmorphismCardFillWidth(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 20.dp,
    glassAlpha: Float = 0.08f,
    borderAlpha: Float = 0.15f,
    contentPadding: PaddingValues = PaddingValues(32.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassmorphismCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = cornerRadius,
        blurRadius = blurRadius,
        glassAlpha = glassAlpha,
        borderAlpha = borderAlpha,
        contentPadding = contentPadding,
        content = content,
    )
}
