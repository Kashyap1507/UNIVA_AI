package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MinimalistCard Component
 *
 * Clean, white card component matching the minimalist design from fitness app mocks.
 * Features:
 * - Pure white background on light gray app background
 * - Generous rounded corners (18dp default)
 * - Generous padding (16-20dp)
 * - Subtle shadow for depth
 * - Optional click handling
 * - Flexible content arrangement
 *
 * Usage:
 * ```
 * MinimalistCard(
 *     onClick = { /* navigate */ }
 * ) {
 *     // Card content
 *     Text("Title")
 *     Text("Description")
 * }
 * ```
 */
@Composable
fun MinimalistCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface, // Pure white
    contentPadding: PaddingValues = PaddingValues(20.dp),
    elevation: Dp = 2.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null, // No ripple for minimalist design
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = elevation,
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * MinimalistCardRow
 *
 * Horizontal variant of MinimalistCard with Row content
 */
@Composable
fun MinimalistCardRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    elevation: Dp = 2.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null, // No ripple for minimalist design
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = elevation,
            ),
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}

/**
 * MinimalistCardBox
 *
 * Box variant of MinimalistCard for layered content
 */
@Composable
fun MinimalistCardBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    elevation: Dp = 2.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null, // No ripple for minimalist design
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = elevation,
            ),
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/**
 * CompactMinimalistCard
 *
 * Smaller variant with reduced padding for dense layouts
 */
@Composable
fun CompactMinimalistCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    MinimalistCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        backgroundColor = backgroundColor,
        contentPadding = contentPadding,
        elevation = elevation,
        content = content,
    )
}

/**
 * SectionCard
 *
 * Card for grouping related content within a section
 * Uses slightly reduced corner radius and elevation for nested appearance
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = elevation,
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
        ) {
            if (title != null) {
                androidx.compose.material3.Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            content()
        }
    }
}

/**
 * CardDivider
 *
 * Subtle divider for use within cards
 */
@Composable
fun CardDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = 1.dp,
    verticalPadding: Dp = 12.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = verticalPadding)
                .height(thickness)
                .background(color),
    )
}

/**
 * GlassCard
 *
 * Semi-transparent card for overlay content
 * Useful for dialogs, modals, or overlays on images
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    MinimalistCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        backgroundColor = backgroundColor,
        contentPadding = contentPadding,
        elevation = elevation,
        content = content,
    )
}
