package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.offline.OperationType
import com.kashyap.wizlyai.offline.QueueState
import com.kashyap.wizlyai.offline.QueuedOperation

/**
 * UX-017: Offline Queue Indicator Component
 *
 * Displays pending offline operations with:
 * - Badge showing count of pending operations
 * - Expandable list view of operations
 * - Manual retry button
 * - Cancel individual operations
 * - Auto-retry indicator
 */
@Composable
fun OfflineQueueIndicator(
    queueState: QueueState,
    operations: List<QueuedOperation>,
    onRetryClick: () -> Unit,
    onCancelOperation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Auto-expand when there are pending operations
    LaunchedEffect(queueState.pendingCount) {
        if (queueState.pendingCount > 0 && !isExpanded) {
            isExpanded = true
        }
    }

    AnimatedVisibility(
        visible = queueState.pendingCount > 0 || queueState.isProcessing,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Header with badge and expand toggle
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Status icon with animation
                        if (queueState.isProcessing) {
                            ProcessingIndicator()
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Column {
                            Text(
                                text = if (queueState.isProcessing) "Processing..." else "Pending Operations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Text(
                                text = "${queueState.pendingCount} operation${if (queueState.pendingCount != 1) "s" else ""} queued",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }

                    // Badge and expand icon
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Pending count badge
                        if (queueState.pendingCount > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .background(
                                            MaterialTheme.colorScheme.error,
                                            CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = queueState.pendingCount.toString(),
                                    color = MaterialTheme.colorScheme.onError,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Expandable operation list
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Operation list
                        operations.forEach { operation ->
                            OperationItem(
                                operation = operation,
                                onCancel = { onCancelOperation(operation.id) },
                            )
                        }

                        // Manual retry button
                        Button(
                            onClick = onRetryClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !queueState.isProcessing && queueState.pendingCount > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Retry All")
                        }

                        // Error message (if any)
                        queueState.lastError?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationItem(
    operation: QueuedOperation,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Operation type icon
                Icon(
                    imageVector = getOperationIcon(operation.type),
                    contentDescription = operation.type.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )

                Column {
                    Text(
                        text = getOperationTitle(operation.type),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    Text(
                        text = "Attempt ${operation.retryCount + 1}/${operation.maxRetries}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            // Cancel button
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProcessingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    Icon(
        imageVector = Icons.Default.Sync,
        contentDescription = "Processing",
        tint = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = rotation },
    )
}

private fun getOperationIcon(type: OperationType) =
    when (type) {
        OperationType.UPLOAD_DOCUMENT -> Icons.Default.CloudUpload
        OperationType.UPDATE_SETTINGS -> Icons.Default.Settings
    }

private fun getOperationTitle(type: OperationType) =
    when (type) {
        OperationType.UPLOAD_DOCUMENT -> "Upload scan"
        OperationType.UPDATE_SETTINGS -> "Update Settings"
    }
