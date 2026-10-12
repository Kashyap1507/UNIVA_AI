package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.theme.GradientBlueTOGreen

/**
 * Animated progress bar with gradient
 */
@Composable
fun WizlyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradient: Brush = GradientBlueTOGreen,
    backgroundColor: Color = Color.LightGray.copy(alpha = 0.3f),
    animated: Boolean = true,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (animated) progress else progress,
        animationSpec = tween(durationMillis = 500),
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(gradient),
        )
    }
}
