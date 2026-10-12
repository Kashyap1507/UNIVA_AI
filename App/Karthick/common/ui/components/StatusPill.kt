package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.ui.theme.*

/**
 * StatusPill Component
 *
 * Small colored badge for displaying status labels like "Good", "Poor", "Stabilizing".
 * Matches the minimalist design from fitness app mocks.
 *
 * Design Features:
 * - Compact pill shape
 * - Color-coded background (success/warning/error)
 * - SemiBold text for clarity
 * - Optional icon
 * - Subtle or bold styling options
 *
 * Usage:
 * ```
 * StatusPill(
 *     text = "Good",
 *     status = Status.SUCCESS
 * )
 * ```
 */

enum class Status {
    SUCCESS, // Good/Positive - teal/mint
    WARNING, // Fair/Warning - orange
    ERROR, // Poor/Negative - salmon/coral
    NEUTRAL, // Default - gray
    INFO, // Informational - blue
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    status: Status = Status.NEUTRAL,
    style: StatusPillStyle = StatusPillStyle.SUBTLE,
    icon: ImageVector? = null,
    iconSize: Dp = 14.dp,
) {
    val (backgroundColor, textColor) =
        when (style) {
            StatusPillStyle.SUBTLE -> status.getSubtleColors()
            StatusPillStyle.BOLD -> status.getBoldColors()
            StatusPillStyle.OUTLINED -> Pair(Color.Transparent, status.getTextColor())
        }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Optional icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = textColor,
            )
        }

        // Status text
        Text(
            text = text,
            style =
                StatusBadgeText.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = textColor,
        )
    }
}

/**
 * StatusPillStyle
 *
 * Visual style options for status pills
 */
enum class StatusPillStyle {
    SUBTLE, // Light background, medium text (default)
    BOLD, // Solid background, white text
    OUTLINED, // Transparent background, colored text
}

/**
 * Status color helpers
 */
private fun Status.getSubtleColors(): Pair<Color, Color> {
    return when (this) {
        Status.SUCCESS -> Pair(Success.copy(alpha = 0.15f), Success)
        Status.WARNING -> Pair(Warning.copy(alpha = 0.15f), Warning)
        Status.ERROR -> Pair(Error.copy(alpha = 0.15f), Error)
        Status.NEUTRAL -> Pair(Color(0xFFE5E5EA), Color(0xFF8E8E93))
        Status.INFO -> Pair(BluePrimary.copy(alpha = 0.15f), BluePrimary)
    }
}

private fun Status.getBoldColors(): Pair<Color, Color> {
    return when (this) {
        Status.SUCCESS -> Pair(Success, Color.White)
        Status.WARNING -> Pair(Warning, Color.White)
        Status.ERROR -> Pair(Error, Color.White)
        Status.NEUTRAL -> Pair(Color(0xFF8E8E93), Color.White)
        Status.INFO -> Pair(BluePrimary, Color.White)
    }
}

private fun Status.getTextColor(): Color {
    return when (this) {
        Status.SUCCESS -> Success
        Status.WARNING -> Warning
        Status.ERROR -> Error
        Status.NEUTRAL -> Color(0xFF8E8E93)
        Status.INFO -> BluePrimary
    }
}

/**
 * Convenience composables for common statuses
 */

@Composable
fun SuccessPill(
    text: String = "Good",
    modifier: Modifier = Modifier,
    style: StatusPillStyle = StatusPillStyle.SUBTLE,
) {
    StatusPill(
        text = text,
        status = Status.SUCCESS,
        style = style,
        modifier = modifier,
    )
}

@Composable
fun WarningPill(
    text: String = "Fair",
    modifier: Modifier = Modifier,
    style: StatusPillStyle = StatusPillStyle.SUBTLE,
) {
    StatusPill(
        text = text,
        status = Status.WARNING,
        style = style,
        modifier = modifier,
    )
}

@Composable
fun ErrorPill(
    text: String = "Poor",
    modifier: Modifier = Modifier,
    style: StatusPillStyle = StatusPillStyle.SUBTLE,
) {
    StatusPill(
        text = text,
        status = Status.ERROR,
        style = style,
        modifier = modifier,
    )
}

@Composable
fun NeutralPill(
    text: String,
    modifier: Modifier = Modifier,
    style: StatusPillStyle = StatusPillStyle.SUBTLE,
) {
    StatusPill(
        text = text,
        status = Status.NEUTRAL,
        style = style,
        modifier = modifier,
    )
}

/**
 * LargeStatusBadge
 *
 * Larger variant for prominent status display
 */
@Composable
fun LargeStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    status: Status = Status.NEUTRAL,
    icon: ImageVector? = null,
) {
    val (backgroundColor, textColor) = status.getSubtleColors()

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = textColor,
            )
        }

        Text(
            text = text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            color = textColor,
        )
    }
}

/**
 * CompactStatusDot
 *
 * Minimal status indicator - just a colored dot with text
 */
@Composable
fun CompactStatusDot(
    text: String,
    modifier: Modifier = Modifier,
    status: Status = Status.NEUTRAL,
    dotSize: Dp = 8.dp,
) {
    val dotColor = status.getTextColor()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Colored dot
        Box(
            modifier =
                Modifier
                    .size(dotSize)
                    .clip(RoundedCornerShape(dotSize / 2))
                    .background(dotColor),
        )

        // Status text
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
