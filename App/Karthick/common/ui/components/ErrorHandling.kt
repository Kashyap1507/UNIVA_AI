package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.util.AppError
import com.kashyap.wizlyai.util.ErrorCategory

/**
 * Standardized Error Handling Components (UX-021)
 *
 * Provides consistent error dialogs, snackbars, and retry mechanisms
 * across the app with user-friendly messages and Material 3 design.
 */

/**
 * Standard error dialog with retry mechanism
 */
@Composable
fun ErrorDialog(
    error: AppError,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    showTechnicalDetails: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expandedDetails by remember { mutableStateOf(false) }

    ThemedAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = getErrorIcon(error.category),
                contentDescription = null,
                tint = getErrorColor(error.category),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = error.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = error.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Technical details (expandable)
                if (showTechnicalDetails && error.technicalMessage != null) {
                    HorizontalDivider()

                    TextButton(
                        onClick = { expandedDetails = !expandedDetails },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Technical Details")
                    }

                    if (expandedDetails) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp),
                        ) {
                            val scrollState = rememberScrollState()
                            Text(
                                text = error.technicalMessage,
                                style = MaterialTheme.typography.bodySmall,
                                modifier =
                                    Modifier
                                        .padding(12.dp)
                                        .verticalScroll(scrollState),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (error.isRetryable && onRetry != null) {
                Button(
                    onClick = {
                        onRetry()
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (error.isRetryable && onRetry != null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/**
 * Inline error state for screens
 */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Error icon
        Icon(
            imageVector = getErrorIcon(error.category),
            contentDescription = null,
            tint = getErrorColor(error.category),
            modifier = Modifier.size(64.dp),
        )

        // Title
        Text(
            text = error.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        // Message
        Text(
            text = error.userMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Retry button
        if (error.isRetryable && onRetry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Small inline error message (for form fields, etc.)
 */
@Composable
fun InlineErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Error snackbar with retry action
 */
suspend fun showErrorSnackbar(
    snackbarHostState: SnackbarHostState,
    error: AppError,
    onRetry: (() -> Unit)? = null,
): SnackbarResult {
    return snackbarHostState.showSnackbar(
        message = error.userMessage,
        actionLabel = if (error.isRetryable && onRetry != null) "Retry" else null,
        duration = if (error.isRetryable) SnackbarDuration.Long else SnackbarDuration.Short,
        withDismissAction = true,
    )
}

/**
 * Get icon for error category
 */
@Composable
private fun getErrorIcon(category: ErrorCategory): ImageVector {
    return when (category) {
        ErrorCategory.NETWORK -> Icons.Default.WifiOff
        ErrorCategory.VALIDATION -> Icons.Default.ErrorOutline
        ErrorCategory.SERVER -> Icons.Default.Error
        ErrorCategory.AUTH -> Icons.Default.Lock
        ErrorCategory.UNKNOWN -> Icons.Default.HelpOutline
    }
}

/**
 * Get color for error category
 */
@Composable
private fun getErrorColor(category: ErrorCategory): androidx.compose.ui.graphics.Color {
    return when (category) {
        ErrorCategory.NETWORK -> MaterialTheme.colorScheme.error
        ErrorCategory.VALIDATION -> MaterialTheme.colorScheme.error
        ErrorCategory.SERVER -> MaterialTheme.colorScheme.error
        ErrorCategory.AUTH -> MaterialTheme.colorScheme.error
        ErrorCategory.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Composable wrapper for error handling logic
 */
@Composable
fun <T> HandleError(
    uiState: com.kashyap.wizlyai.ui.state.UiState<T>,
    onRetry: (() -> Unit)? = null,
    showDialog: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    when (uiState) {
        is com.kashyap.wizlyai.ui.state.UiState.Success -> {
            content(uiState.data)
        }
        is com.kashyap.wizlyai.ui.state.UiState.Error -> {
            val error =
                AppError.fromException(
                    exception = Exception(uiState.message),
                    context = "Data loading failed",
                )

            if (showDialog) {
                ErrorDialog(
                    error = error,
                    onDismiss = { /* Handle dismiss */ },
                    onRetry = onRetry,
                )
            } else {
                ErrorState(
                    error = error,
                    onRetry = onRetry,
                )
            }
        }
        is com.kashyap.wizlyai.ui.state.UiState.Loading -> {
            // Show loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is com.kashyap.wizlyai.ui.state.UiState.Idle -> {
            // Idle state - do nothing or show empty state
        }
    }
}
