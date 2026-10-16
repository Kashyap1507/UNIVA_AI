package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.GlassmorphismCard
import com.kashyap.wizlyai.ui.components.GlowLoadingButton
import com.kashyap.wizlyai.ui.icons.BrandIcons
import com.kashyap.wizlyai.ui.theme.Foreground
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.theme.authBackgroundGradient
import com.kashyap.wizlyai.ui.viewmodel.AuthState
import com.kashyap.wizlyai.ui.viewmodel.AuthViewModel

/**
 * Tracks which authentication method is currently being used
 */
private enum class SignupAuthMethod {
    NONE, GOOGLE, APPLE, EMAIL
}

/**
 * Signup Screen - Redesigned to match Wizly-web screenshot
 * White card on gradient background with person icon, social auth, and name/email inputs
 */
@Composable
fun SignupScreen(
    viewModel: AuthViewModel,
    onNavigateToOTP: (email: String, action: String, referralCode: String?) -> Unit,
    onNavigateToLogin: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onAppleSignIn: () -> Unit = {},
    onNavigateToHome: (String, Boolean) -> Unit = { _, _ -> }, // userId, needsOnboarding
    referralCode: String? = null,
) {
    var referralInput by remember { mutableStateOf(referralCode.orEmpty()) }
    val enteredReferralCode = referralInput.trim().takeIf { it.isNotEmpty() }
    var email by remember { mutableStateOf("") }
    var activeAuthMethod by remember { mutableStateOf(SignupAuthMethod.NONE) }

    val signupState by viewModel.signupState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val providerAuthState by viewModel.providerAuthState.collectAsState()

    // Compute loading states based on which auth method was triggered
    val isAnyAuthLoading = providerAuthState is UiState.Loading || signupState is UiState.Loading
    val isGoogleLoading = activeAuthMethod == SignupAuthMethod.GOOGLE && providerAuthState is UiState.Loading
    val isAppleLoading = activeAuthMethod == SignupAuthMethod.APPLE && providerAuthState is UiState.Loading
    val isEmailLoading = activeAuthMethod == SignupAuthMethod.EMAIL && signupState is UiState.Loading

    // Reset active auth method when loading completes
    LaunchedEffect(providerAuthState, signupState) {
        if (providerAuthState !is UiState.Loading && signupState !is UiState.Loading) {
            activeAuthMethod = SignupAuthMethod.NONE
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.VerificationRequired) {
            val verificationState = authState as AuthState.VerificationRequired
            onNavigateToOTP(verificationState.email, verificationState.action, enteredReferralCode)
        }
    }

    // Handle Google Sign-In success - trigger navigation directly from UI
    LaunchedEffect(providerAuthState) {
        println("🔍 SignupScreen: providerAuthState changed to ${providerAuthState::class.simpleName}")
        if (providerAuthState is com.kashyap.wizlyai.ui.state.UiState.Success) {
            println("✅ SignupScreen: Provider auth SUCCESS!")
            // Get user data directly from success response
            val authResponse = (providerAuthState as com.kashyap.wizlyai.ui.state.UiState.Success).data
            val userId = authResponse.user?.id

            if (userId != null) {
                onNavigateToHome(userId, authResponse.needsOnboarding)
            } else {
                println("❌ SignupScreen: User ID is NULL in auth response - cannot navigate")
            }
        } else if (providerAuthState is com.kashyap.wizlyai.ui.state.UiState.Error) {
            println("❌ SignupScreen: Provider auth ERROR: ${(providerAuthState as com.kashyap.wizlyai.ui.state.UiState.Error).message}")
        }
    }

    val isEmailValid = email.contains("@") && email.contains(".")
    val isFormValid = isEmailValid
    val isDarkTheme = isSystemInDarkTheme()

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
                    .statusBarsPadding() // UX-027: Respect iOS notch/Dynamic Island
                    .navigationBarsPadding() // UX-027: Respect iOS home indicator
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Glassmorphism card (glass effect in dark mode, white card in light mode)
            GlassmorphismCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
            ) {
                    // Dim header content when loading
                    val headerAlpha = if (isAnyAuthLoading) 0.3f else 1f

                    // Person icon in light blue circle
                    Box(
                        modifier =
                            Modifier
                                .alpha(headerAlpha)
                                .size(80.dp)
                                .background(
                                    color = BluePrimary.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                )
                                .decorative(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(headerAlpha).heading(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        text = "Your AI study partner for university",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(headerAlpha),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Google button with glow effect
                    GlowLoadingButton(
                        text = "Continue with Google",
                        onClick = {
                            activeAuthMethod = SignupAuthMethod.GOOGLE
                            onGoogleSignIn()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = BrandIcons.Google,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isDarkTheme) Color.White else Foreground,
                            )
                        },
                        isLoading = isGoogleLoading,
                        loadingText = "Getting your data ready...",
                        enabled = !isAnyAuthLoading,
                        glowColor = if (isDarkTheme) Color.White else BluePrimary,
                        glowAlpha = if (isDarkTheme) 0.2f else 0.3f,
                        backgroundColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.White,
                        contentColor = if (isDarkTheme) Color.White else Foreground,
                        borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Apple button with glow effect
                    GlowLoadingButton(
                        text = "Continue with Apple",
                        onClick = {
                            activeAuthMethod = SignupAuthMethod.APPLE
                            onAppleSignIn()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = BrandIcons.Apple,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isDarkTheme) Color.White else Foreground,
                            )
                        },
                        isLoading = isAppleLoading,
                        loadingText = "Getting your data ready...",
                        enabled = !isAnyAuthLoading,
                        glowColor = if (isDarkTheme) Color.White else BluePrimary,
                        glowAlpha = if (isDarkTheme) 0.2f else 0.3f,
                        backgroundColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.White,
                        contentColor = if (isDarkTheme) Color.White else Foreground,
                        borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "or" divider
                    Text(
                        text = "or",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(headerAlpha),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email input
                    Column(
                        modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .textFieldSemantics(
                                        label = "Email address",
                                        value = email,
                                        hint = "Enter your email address",
                                        error =
                                            if (email.isNotEmpty() && !isEmailValid) {
                                                "Please enter a valid email address"
                                            } else {
                                                null
                                            },
                                    ),
                            placeholder = {
                                Text(
                                    "Enter your email",
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.White,
                                    unfocusedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.White,
                                    focusedTextColor = if (isDarkTheme) Color.White else Foreground,
                                    unfocusedTextColor = if (isDarkTheme) Color.White else Foreground,
                                    cursorColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary,
                                ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.decorative(),
                                )
                            },
                        )

                        if (email.isNotEmpty() && !isEmailValid) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please enter a valid email address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.errorSemantics("Please enter a valid email address"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Referral code (optional)
                    Column(
                        modifier = Modifier.fillMaxWidth().alpha(headerAlpha),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Referral code (optional)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = referralInput,
                            onValueChange = { referralInput = it.uppercase() },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .textFieldSemantics(
                                        label = "Referral code",
                                        value = referralInput,
                                        hint = "Enter a referral code if you have one",
                                        error = null,
                                    ),
                            placeholder = {
                                Text(
                                    "e.g. WIZ123",
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.White,
                                    unfocusedContainerColor = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.White,
                                    focusedTextColor = if (isDarkTheme) Color.White else Foreground,
                                    unfocusedTextColor = if (isDarkTheme) Color.White else Foreground,
                                    cursorColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary,
                                ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Continue with Email button with glow effect
                    GlowLoadingButton(
                        text = "Continue with Email",
                        onClick = {
                            activeAuthMethod = SignupAuthMethod.EMAIL
                            viewModel.initiateSignup(email, enteredReferralCode)
                        },
                        isLoading = isEmailLoading,
                        loadingText = "Sending verification code...",
                        enabled = isFormValid && !isAnyAuthLoading,
                        glowColor = BluePrimary,
                        glowAlpha = 0.4f,
                        backgroundColor = BluePrimary,
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign in link
                    TextButton(
                        onClick = onNavigateToLogin,
                        enabled = !isAnyAuthLoading,
                        modifier = Modifier.alpha(headerAlpha).linkSemantics("Sign in to your account"),
                    ) {
                        Text(
                            text = "Already have an account? Sign in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        }
    }
}
