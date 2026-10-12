package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Toast Types for different notification categories (UX-015)
 */
enum class ToastType {
    SUCCESS, // Green - successful operations
    ERROR, // Red - errors and failures
    INFO, // Blue - informational messages
    WARNING, // Orange - warnings and alerts
}

/**
 * Toast data container (UX-015)
 */
data class ToastData(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
)

/**
 * Material 3 Toast/Snackbar Manager (UX-015)
 *
 * Provides standardized user feedback for operations with:
 * - Color-coded toast types (success, error, info, warning)
 * - Material 3 design with icons
 * - Optional action buttons
 * - Accessibility support
 * - Queue management (automatic sequential display)
 *
 * Usage:
 * ```kotlin
 * val snackbarHostState = remember { SnackbarHostState() }
 * val scope = rememberCoroutineScope()
 *
 * // Show toast
 * scope.launch {
 *     showToast(
 *         snackbarHostState = snackbarHostState,
 *         message = "Document saved successfully",
 *         type = ToastType.SUCCESS
 *     )
 * }
 *
 * // In Scaffold
 * Scaffold(
 *     snackbarHost = { WizlySnackbarHost(snackbarHostState) }
 * ) { ... }
 * ```
 *
 * @param snackbarHostState SnackbarHostState for managing snackbar display
 * @param modifier Optional modifier
 */
@Composable
fun WizlySnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
    ) { data ->
        // Parse toast type from message prefix (workaround for custom data)
        val (type, cleanMessage) = parseToastMessage(data.visuals.message)

        WizlySnackbar(
            message = cleanMessage,
            type = type,
            actionLabel = data.visuals.actionLabel,
            onActionClick = { data.performAction() },
            onDismiss = { data.dismiss() },
        )
    }
}

/**
 * Styled Snackbar with toast type support (UX-015)
 */
@Composable
fun WizlySnackbar(
    message: String,
    type: ToastType,
    actionLabel: String? = null,
    onActionClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, icon) = getToastStyle(type)

    Snackbar(
        modifier = modifier.padding(12.dp),
        action =
            if (actionLabel != null) {
                {
                    TextButton(onClick = onActionClick) {
                        Text(
                            text = actionLabel,
                            color = contentColor,
                        )
                    }
                }
            } else {
                null
            },
        dismissAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor,
                )
            }
        },
        containerColor = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Get color scheme and icon for toast type
 */
@Composable
private fun getToastStyle(type: ToastType): Triple<Color, Color, ImageVector> {
    return when (type) {
        ToastType.SUCCESS ->
            Triple(
                Color(0xFF059669), // Green-600
                Color.White,
                Icons.Default.CheckCircle,
            )
        ToastType.ERROR ->
            Triple(
                Color(0xFFDC2626), // Red-600
                Color.White,
                Icons.Default.Error,
            )
        ToastType.INFO ->
            Triple(
                Color(0xFF2563EB), // Blue-600
                Color.White,
                Icons.Default.Info,
            )
        ToastType.WARNING ->
            Triple(
                Color(0xFFEA580C), // Orange-600
                Color.White,
                Icons.Default.Warning,
            )
    }
}

/**
 * Parse toast type from message prefix
 * Message format: "[TYPE]actual message"
 */
private fun parseToastMessage(message: String): Pair<ToastType, String> {
    return when {
        message.startsWith("[SUCCESS]") -> Pair(ToastType.SUCCESS, message.removePrefix("[SUCCESS]"))
        message.startsWith("[ERROR]") -> Pair(ToastType.ERROR, message.removePrefix("[ERROR]"))
        message.startsWith("[WARNING]") -> Pair(ToastType.WARNING, message.removePrefix("[WARNING]"))
        message.startsWith("[INFO]") -> Pair(ToastType.INFO, message.removePrefix("[INFO]"))
        else -> Pair(ToastType.INFO, message)
    }
}

/**
 * Helper function to show toast (UX-015)
 * Automatically queues if another toast is showing
 */
suspend fun showToast(
    snackbarHostState: SnackbarHostState,
    message: String,
    type: ToastType = ToastType.INFO,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
): SnackbarResult {
    // Prefix message with type for parsing in WizlySnackbarHost
    val prefixedMessage = "[${type.name}]$message"

    return snackbarHostState.showSnackbar(
        message = prefixedMessage,
        actionLabel = actionLabel,
        duration = duration,
        withDismissAction = true,
    )
}

/**
 * Convenience functions for common toast types (UX-015)
 */
suspend fun showSuccessToast(
    snackbarHostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
) = showToast(snackbarHostState, message, ToastType.SUCCESS, actionLabel, duration)

suspend fun showErrorToast(
    snackbarHostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Long, // Errors stay longer
) = showToast(snackbarHostState, message, ToastType.ERROR, actionLabel, duration)

suspend fun showInfoToast(
    snackbarHostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
) = showToast(snackbarHostState, message, ToastType.INFO, actionLabel, duration)

suspend fun showWarningToast(
    snackbarHostState: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Long, // Warnings stay longer
) = showToast(snackbarHostState, message, ToastType.WARNING, actionLabel, duration)
