package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Error Retry Dialog
 *
 * Shows error message with Retry and Cancel buttons
 *
 * @param errorMessage Error message to display
 * @param onRetry Callback when user taps Retry
 * @param onDismiss Callback when user taps Cancel or dismisses dialog
 * @param retryButtonText Text for retry button (default: "Retry")
 * @param cancelButtonText Text for cancel button (default: "Cancel")
 */
@Composable
fun ErrorRetryDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    retryButtonText: String = "Retry",
    cancelButtonText: String = "Cancel",
    modifier: Modifier = Modifier,
) {
    ThemedAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Something went wrong",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = retryButtonText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = cancelButtonText,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B7280),
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}
