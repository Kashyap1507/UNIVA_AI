package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * CircularGauge Component
 *
 * Circular progress indicator matching the minimalist design from fitness app mocks.
 * Used for displaying metrics like Strain, Recovery, Sleep with percentage values.
 *
 * Design Features:
 * - Clean circular track with subtle background
 * - Smooth animated progress arc
 * - Large centered percentage value
 * - Optional label below value
 * - Color-coded progress (success/warning/error)
 * - Small decorative indicator dot at progress end
 *
 * Usage:
 * ```
 * CircularGauge(
 *     progress = 0.65f,
 *     label = "Strain",
 *     size = 140.dp
 * )
 * ```
 */
@Composable
fun CircularGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 140.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color? = null, // Auto-determined based on progress if null
    strokeWidth: Dp = 12.dp,
    showDot: Boolean = true,
    animationDuration: Int = 800,
) {
    // Auto-determine color based on progress value
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
        label = "progress_animation",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Circular gauge canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (canvasSize / 2f) - strokeWidthPx / 2f
            val center = Offset(canvasSize / 2f, canvasSize / 2f)

            // Background track (full circle)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft =
                    Offset(
                        x = center.x - radius,
                        y = center.y - radius,
                    ),
                size = Size(radius * 2, radius * 2),
                style =
                    Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                    ),
            )

            // Progress arc
            if (animatedValue > 0f) {
                drawArc(
                    color = finalProgressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedValue,
                    useCenter = false,
                    topLeft =
                        Offset(
                            x = center.x - radius,
                            y = center.y - radius,
                        ),
                    size = Size(radius * 2, radius * 2),
                    style =
                        Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                        ),
                )

                // Progress end dot indicator
                if (showDot && animatedValue > 0.05f) {
                    val angle = (-90f + 360f * animatedValue) * (PI / 180f)
                    val dotRadius = strokeWidthPx / 2f
                    val dotX = center.x + radius * cos(angle).toFloat()
                    val dotY = center.y + radius * sin(angle).toFloat()

                    drawCircle(
                        color = finalProgressColor,
                        radius = dotRadius,
                        center = Offset(dotX, dotY),
                    )
                }
            }
        }

        // Center content (percentage and label)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            // Large percentage value
            Text(
                text = "${(animatedValue * 100).toInt()}%",
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontSize = (size.value * 0.25f).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (size.value * 0.25f).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Optional label
            if (label != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Circular Gauge with Gradient Progress
 *
 * Alternative version with gradient progress arc (e.g., blue-to-teal)
 */
@Composable
fun CircularGaugeGradient(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 140.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressGradient: Brush =
        Brush.sweepGradient(
            colors = listOf(BluePrimary, TealPrimary),
        ),
    strokeWidth: Dp = 12.dp,
    showDot: Boolean = true,
    animationDuration: Int = 800,
) {
    // Animate progress
    var animatedProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(progress) {
        animatedProgress = progress
    }

    val animatedValue by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = animationDuration),
        label = "progress_animation",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Circular gauge canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (canvasSize / 2f) - strokeWidthPx / 2f
            val center = Offset(canvasSize / 2f, canvasSize / 2f)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft =
                    Offset(
                        x = center.x - radius,
                        y = center.y - radius,
                    ),
                size = Size(radius * 2, radius * 2),
                style =
                    Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                    ),
            )

            // Progress arc with gradient
            if (animatedValue > 0f) {
                drawArc(
                    brush = progressGradient,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedValue,
                    useCenter = false,
                    topLeft =
                        Offset(
                            x = center.x - radius,
                            y = center.y - radius,
                        ),
                    size = Size(radius * 2, radius * 2),
                    style =
                        Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                        ),
                )

                // Progress end dot
                if (showDot && animatedValue > 0.05f) {
                    val angle = (-90f + 360f * animatedValue) * (PI / 180f)
                    val dotRadius = strokeWidthPx / 2f
                    val dotX = center.x + radius * cos(angle).toFloat()
                    val dotY = center.y + radius * sin(angle).toFloat()

                    drawCircle(
                        color = TealPrimary,
                        radius = dotRadius,
                        center = Offset(dotX, dotY),
                    )
                }
            }
        }

        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "${(animatedValue * 100).toInt()}%",
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontSize = (size.value * 0.25f).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (size.value * 0.25f).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (label != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
