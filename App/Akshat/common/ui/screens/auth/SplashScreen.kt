package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.theme.TealPrimary
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient
import kotlinx.coroutines.delay

/**
 * Splash Screen - Auto-login flow
 * - Checks for stored session on startup
 * - Auto-navigates to Home if valid session exists
 * - Shows onboarding/auth if no session or session expired
 */
@Composable
fun SplashScreen(
    onNavigateNext: () -> Unit,
    onNavigateToHome: () -> Unit = {},
) {
    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = EaseOut),
    )
    val logoScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = EaseOut),
    )

    // Sparkles rotation animation
    val infiniteTransition = rememberInfiniteTransition()
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    LaunchedEffect(Unit) {
        // Wait for minimum splash duration
        delay(1500)

        // Try to restore saved session
        val sessionRestored = UserSessionManager.instance.restoreSession()

        if (sessionRestored) {
            // Valid session found - auto-login to Home
            onNavigateToHome()
        } else {
            // No valid session - check if onboarding was seen
            val hasSeenOnboarding = UserSessionManager.instance.hasSeenOnboarding()

            if (hasSeenOnboarding) {
                // Skip onboarding, go straight to auth landing
                onNavigateNext() // This should navigate to AuthLanding
            } else {
                // Show onboarding first
                onNavigateNext() // This will show Onboarding
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .loadingSemantics("Loading Univa AI, checking authentication"),
    ) {
        // Background gradient fills entire screen (edge-to-edge)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(authBackgroundGradient()),
        )

        // Content with safe area padding
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(logoAlpha).scale(logoScale),
            ) {
                // Gradient background effect behind logo with sparkles
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.decorative(),
                ) {
                    // Sparkles around logo
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .offset(x = (-60).dp, y = (-40).dp)
                                .rotate(sparkleRotation)
                                .decorative(),
                        tint = BluePrimary.copy(alpha = 0.6f),
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(20.dp)
                                .offset(x = 60.dp, y = (-50).dp)
                                .rotate(-sparkleRotation)
                                .decorative(),
                        tint = TealPrimary.copy(alpha = 0.5f),
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .offset(x = (-50).dp, y = 50.dp)
                                .rotate(sparkleRotation * 0.7f)
                                .decorative(),
                        tint = TealPrimary.copy(alpha = 0.4f),
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .offset(x = 55.dp, y = 45.dp)
                                .rotate(-sparkleRotation * 0.8f)
                                .decorative(),
                        tint = BluePrimary.copy(alpha = 0.5f),
                    )

                    UnivaLogo(size = 110.dp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App name
                Text(
                    text = "Univa AI",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    // headlineLarge now uses Bold by default in new typography
                )
            }

            // Version number at bottom
            Text(
                text = "v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
            )
        }
    }
}
