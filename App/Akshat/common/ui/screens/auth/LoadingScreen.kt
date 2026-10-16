package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Loading Screen - Per Wizly-Web specification
 * Pure white background, logo slides from center to top, pulsing dots indicator
 * Typically shown for 2.5 seconds during initial app load
 */
@Composable
fun LoadingScreen(onLoadComplete: () -> Unit = {}) {
    val logoY = remember { Animatable(0f) } // Position offset (0 = center, -200 = top)
    val logoScale = remember { Animatable(1.5f) }
    val textAlpha = remember { Animatable(0f) }
    val dotsAlpha = remember { Animatable(0f) }

    // Three pulsing dots
    val dot1Alpha = remember { Animatable(0.3f) }
    val dot1Scale = remember { Animatable(1f) }
    val dot2Alpha = remember { Animatable(0.3f) }
    val dot2Scale = remember { Animatable(1f) }
    val dot3Alpha = remember { Animatable(0.3f) }
    val dot3Scale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Logo slides from center to top and scales down
        launch {
            logoY.animateTo(
                targetValue = -200f,
                animationSpec = tween(durationMillis = 800, easing = EaseInOut),
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = EaseInOut),
            )
        }

        // Text fades in after logo movement starts
        delay(400)
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600),
            )
        }

        // Dots fade in and start pulsing
        delay(800)
        launch {
            dotsAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400),
            )
        }

        // Pulsing animation for dot 1
        launch {
            delay(800)
            dot1Alpha.animateTo(
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }
        launch {
            delay(800)
            dot1Scale.animateTo(
                targetValue = 1.2f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }

        // Pulsing animation for dot 2 (delayed)
        launch {
            delay(1000)
            dot2Alpha.animateTo(
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }
        launch {
            delay(1000)
            dot2Scale.animateTo(
                targetValue = 1.2f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }

        // Pulsing animation for dot 3 (delayed)
        launch {
            delay(1200)
            dot3Alpha.animateTo(
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }
        launch {
            delay(1200)
            dot3Scale.animateTo(
                targetValue = 1.2f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }

        // Complete loading after 2.5 seconds total
        delay(2500)
        onLoadComplete()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(authBackgroundGradient())
                .semantics {
                    contentDescription = "Loading Univa AI. Please wait."
                    liveRegion = LiveRegionMode.Polite
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo that slides from center to top
            UnivaLogo(
                size = (64 * logoScale.value).dp,
                color = Color.White,
                modifier =
                    Modifier
                        .offset(y = logoY.value.dp)
                        .decorative(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // App name and subtitle (fade in after logo movement)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value),
            ) {
                Text(
                    text = "Univa AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your AI study assistant",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Pulsing dots indicator at bottom
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .alpha(dotsAlpha.value)
                    .decorative(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Dot 1
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .scale(dot1Scale.value)
                            .alpha(dot1Alpha.value)
                            .background(
                                color = BluePrimary.copy(alpha = 0.6f),
                                shape = CircleShape,
                            ),
                )

                // Dot 2
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .scale(dot2Scale.value)
                            .alpha(dot2Alpha.value)
                            .background(
                                color = BluePrimary.copy(alpha = 0.6f),
                                shape = CircleShape,
                            ),
                )

                // Dot 3
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .scale(dot3Scale.value)
                            .alpha(dot3Alpha.value)
                            .background(
                                color = BluePrimary.copy(alpha = 0.6f),
                                shape = CircleShape,
                            ),
                )
            }
        }
    }
}
