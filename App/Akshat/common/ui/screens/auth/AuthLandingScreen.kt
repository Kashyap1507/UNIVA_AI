package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient

/**
 * Welcome to Univa AI Screen - Shown after onboarding
 * Simple welcome screen with logo and Get Started button
 */
@Composable
fun AuthLandingScreen(
    onHaveAccount: () -> Unit,
    onGetStarted: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(authBackgroundGradient()),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            UnivaLogo(
                size = 120.dp,
                color = Color.White,
                modifier = Modifier.decorative(),
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Title
            Text(
                text = "Welcome to Univa AI",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.heading(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            Text(
                text = "Your courses, notes and exams,\nwith an AI study partner.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            // White inverted button
            Button(
                onClick = onGetStarted,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .accessibleButton(
                            label = "Get started with Univa AI",
                            enabled = true,
                        ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = com.kashyap.wizlyai.ui.theme.BluePrimary,
                    ),
                shape = RoundedCornerShape(14.dp), // Updated to 14dp for consistency
                elevation =
                    ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp,
                    ),
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold, // SemiBold for button text
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign in text link
            TextButton(
                onClick = onHaveAccount,
                modifier = Modifier.linkSemantics("Sign in to your account"),
            ) {
                Text(
                    text = "Already have an account? Sign In",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
