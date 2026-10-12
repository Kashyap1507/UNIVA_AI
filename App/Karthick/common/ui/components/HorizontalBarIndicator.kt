package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.ui.theme.*

/**
 * HorizontalBarIndicator Component
 *
 * Horizontal progress bar matching the minimalist design from fitness app mocks.
 * Used for displaying metrics like VO2 Max, HRV, or any range-based value.
 *
 * Design Features:
 * - Thin horizontal bar with subtle background
 * - Smooth animated progress indicator
 * - Color-coded ranges (good/warning/poor zones)
 * - Optional range markers
 * - Clean, minimal aesthetic
 *
 * Usage:
 * ```
 * HorizontalBarIndicator(
 *     value = 31.9f,
 *     minValue = 0f,
 *     maxValue = 60f,
 *     label = "VO₂ Max",
 *     status = "Poor"
 * )
 * ```
 */
@Composable
fun HorizontalBarIndicator(
    value: Float,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    status: String? = null,
    height: Dp = 24.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color? = null, // Auto-determined if null
    showRangeMarkers: Boolean = false,
    animationDuration: Int = 800,
) {
    // Calculate progress (0.0 to 1.0)
    val progress = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    // Auto-determine color based on progress
    val autoProgressColor =
        when {
            progress >= 0.7f -> Success // Good: >= 70%
            progress >= 0.4f -> Warning // Warning: 40-69%
            else -> Error // Poor: < 40%
        }

    val finalProgressColor = progressColor ?: autoProgressColor

    // Animate progress
    var animatedProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(progress) {
        animatedProgress = progress
    }

    val animatedValue by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = animationDuration),
        label = "bar_progress_animation",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Label and status row (if provided)
        if (label != null || status != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (status != null) {
                    Text(
                        text = status,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = finalProgressColor,
                    )
                }
            }
        }

        // Horizontal bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(height / 2)) // Fully rounded ends
                    .background(trackColor),
        ) {
            // Progress fill
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedValue)
                        .clip(RoundedCornerShape(height / 2))
                        .background(finalProgressColor),
            )
        }

        // Range markers (optional)
        if (showRangeMarkers) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = minValue.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = maxValue.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * HorizontalBarIndicatorWithValue
 *
 * Variant that displays the numeric value prominently above the bar
 */
@Composable
fun HorizontalBarIndicatorWithValue(
    value: Float,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    unit: String = "",
    status: String? = null,
    height: Dp = 20.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color? = null,
    animationDuration: Int = 800,
) {
    val progress = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    val autoProgressColor =
        when {
            progress >= 0.7f -> Success
            progress >= 0.4f -> Warning
            else -> Error
        }

    val finalProgressColor = progressColor ?: autoProgressColor

    var animatedProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(progress) {
        animatedProgress = progress
    }

    val animatedValue by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = animationDuration),
        label = "bar_progress_animation",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Large value display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                // Value
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = value.toInt().toString(),
                        style =
                            DisplayMetric.copy(
                                fontSize = 40.sp,
                                lineHeight = 40.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                // Status
                if (status != null) {
                    Text(
                        text = status,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = finalProgressColor,
                    )
                }
            }
        }

        // Horizontal bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(trackColor),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedValue)
                        .clip(RoundedCornerShape(height / 2))
                        .background(finalProgressColor),
            )
        }

        // Label (if provided)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * MultiZoneHorizontalBar
 *
 * Horizontal bar with multiple colored zones (e.g., Poor/Fair/Good)
 * Used for metrics with clear performance ranges
 */
@Composable
fun MultiZoneHorizontalBar(
    value: Float,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
    zones: List<Zone> = defaultZones,
    height: Dp = 24.dp,
    showIndicator: Boolean = true,
    animationDuration: Int = 800,
) {
    val progress = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    var animatedProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(progress) {
        animatedProgress = progress
    }

    val animatedValue by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = animationDuration),
        label = "multi_zone_animation",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2)),
    ) {
        // Draw zones
        Row(modifier = Modifier.fillMaxSize()) {
            zones.forEach { zone ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(zone.weight)
                            .background(zone.color.copy(alpha = 0.3f)),
                )
            }
        }

        // Progress indicator (simplified - just a vertical line)
        if (showIndicator && animatedValue > 0.02f) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(animatedValue)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(height / 2))
                        .background(
                            zones.firstOrNull {
                                animatedValue >= it.rangeStart && animatedValue < it.rangeEnd
                            }?.color ?: zones.last().color,
                        ),
            )
        }
    }
}

/**
 * Zone data class for MultiZoneHorizontalBar
 */
data class Zone(
    val rangeStart: Float,
    val rangeEnd: Float,
    val color: Color,
    val label: String,
) {
    val weight: Float get() = rangeEnd - rangeStart
}

// Default zones: Poor (0-40%), Fair (40-70%), Good (70-100%)
val defaultZones =
    listOf(
        Zone(0f, 0.4f, Error, "Poor"),
        Zone(0.4f, 0.7f, Warning, "Fair"),
        Zone(0.7f, 1.0f, Success, "Good"),
    )
