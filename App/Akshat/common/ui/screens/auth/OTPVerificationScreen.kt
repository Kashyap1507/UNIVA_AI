package com.kashyap.wizlyai.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.LoadingState
import com.kashyap.wizlyai.ui.components.PrimaryButton
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.viewmodel.AuthViewModel

/**
 * OTP Verification Screen - Redesigned per Wizly-Web specification
 * 5 individual input boxes (56dp×56dp), auto-advance, countdown timer
 */
/** Must match the server's VERIFICATION_CODE_LENGTH (app/config.py). */
private const val OTP_LENGTH = 6

@Composable
fun OTPVerificationScreen(
    viewModel: AuthViewModel,
    email: String,
    action: String,
    referralCode: String? = null,
    onVerificationSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var otpDigits by remember { mutableStateOf(List(OTP_LENGTH) { "" }) }
    var resendCooldown by remember { mutableStateOf(60) }
    var canResend by remember { mutableStateOf(false) }

    val verifyState by viewModel.verifyState.collectAsState()

    // Start countdown timer
    LaunchedEffect(Unit) {
        while (resendCooldown > 0) {
            kotlinx.coroutines.delay(1000)
            resendCooldown -= 1
        }
        canResend = true
    }

    // Auto-verify when all digits are filled
    LaunchedEffect(otpDigits) {
        val otpCode = otpDigits.joinToString("")
        if (otpCode.length == OTP_LENGTH && otpCode.all { it.isDigit() }) {
            kotlinx.coroutines.delay(500) // Brief delay before auto-submit
            viewModel.verifyCode(
                email = email,
                code = otpCode,
                action = action,
                referralCode = referralCode,
            )
        }
    }

    LaunchedEffect(verifyState) {
        if (verifyState is UiState.Success) {
            onVerificationSuccess()
            viewModel.resetVerifyState()
        }
    }

    if (verifyState is UiState.Loading) {
        LoadingState(message = "Getting your data ready...")
        return
    }

    val isCodeValid = otpDigits.all { it.isNotEmpty() }

    AuthScreenContainer(
        headerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Verify Email",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, // Bold for emphasis on headlines
                color = Color.White,
                modifier = Modifier.heading(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We've sent a $OTP_LENGTH-digit code to",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
        },
    ) {
        AuthSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // One input box per code digit
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(OTP_LENGTH) { index ->
                    OutlinedTextField(
                        value = otpDigits[index],
                        onValueChange = { value ->
                            if (value.length == OTP_LENGTH && value.all { it.isDigit() }) {
                                // Pasted or autofilled full code
                                otpDigits = value.map { it.toString() }
                            } else if (value.length <= 1 && value.all { it.isDigit() }) {
                                val newDigits = otpDigits.toMutableList()
                                newDigits[index] = value
                                otpDigits = newDigits
                            }
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .semantics {
                                    contentDescription = "Digit ${index + 1} of $OTP_LENGTH"
                                },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.headlineMedium.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold, // Bold for OTP digits
                            ),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor =
                                    if (otpDigits[index].isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                focusedContainerColor =
                                    if (otpDigits[index].isNotEmpty()) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                                    } else {
                                        Color.Transparent
                                    },
                                unfocusedContainerColor =
                                    if (otpDigits[index].isNotEmpty()) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                                    } else {
                                        Color.Transparent
                                    },
                            ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            TextButton(
                onClick = {
                    canResend = false
                    resendCooldown = 60 // 60 seconds cooldown
                    if (action == "signup") {
                        viewModel.initiateSignup(email, referralCode)
                    } else {
                        viewModel.initiateLogin(email)
                    }
                },
                enabled = canResend,
            ) {
                Text(
                    text =
                        if (canResend) {
                            "Didn't receive the code? Resend"
                        } else {
                            "Resend code in ${resendCooldown}s"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (canResend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                )
            }
        }

        if (verifyState is UiState.Error) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = (verifyState as UiState.Error).message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        AuthBottomSection {
            PrimaryButton(
                text = "Verify code",
                onClick = {
                    val otpCode = otpDigits.joinToString("")
                    viewModel.verifyCode(
                        email = email,
                        code = otpCode,
                        action = action,
                        referralCode = referralCode,
                    )
                },
                enabled = isCodeValid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
