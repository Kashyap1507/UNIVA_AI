package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient

@Composable
fun AuthScreenContainer(
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    logoSize: Dp = 88.dp,
    headerEnterTransition: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { it / 10 },
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            ),
    enterTransition: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            ),
    headerContent: @Composable ColumnScope.() -> Unit = {},
    bodyContent: @Composable ColumnScope.() -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(authBackgroundGradient()),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f)),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLogo) {
                UnivaLogo(size = logoSize, color = Color.White)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = headerEnterTransition,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    headerContent()
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = enterTransition,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    bodyContent()
                }
            }
        }
    }
}

/**
 * Univa AI logo: a rounded "U" with an AI sparkle, drawn without a tile behind it.
 * [color] null uses the brand gradient (for plain backgrounds); pass White on the gradient screens.
 * Same geometry as the app icon (100-unit box).
 */
@Composable
fun UnivaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brush =
        if (color != null) {
            SolidColor(color)
        } else if (dark) {
            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF34BEAC)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF2A9D8F)))
        }

    Canvas(modifier = modifier.size(size)) {
        val k = this.size.minDimension / 100f
        val stroke = 18.4f * k
        val left = 2.4f * k + stroke / 2
        val right = 76f * k - stroke / 2
        val top = 26.4f * k
        val radius = (right - left) / 2
        val arcBottom = 100f * k - stroke / 2

        val u =
            Path().apply {
                moveTo(left, top)
                lineTo(left, arcBottom - radius)
                arcTo(
                    rect = Rect(left, arcBottom - 2 * radius, right, arcBottom),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -180f,
                    forceMoveTo = false,
                )
                lineTo(right, top)
            }
        drawPath(u, brush, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Four-point sparkle
        val cx = 84f * k
        val cy = 13.6f * k
        val r = 13.6f * k
        val n = r * 0.28f
        val sparkle =
            Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + n, cy - n)
                lineTo(cx + r, cy)
                lineTo(cx + n, cy + n)
                lineTo(cx, cy + r)
                lineTo(cx - n, cy + n)
                lineTo(cx - r, cy)
                lineTo(cx - n, cy - n)
                close()
            }
        drawPath(sparkle, brush)
    }
}

@Composable
fun AuthSurfaceCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(24.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
        ) {
            content()
        }
    }
}

@Composable
fun AuthBottomSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
