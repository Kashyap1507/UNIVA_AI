package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Badge showing document sync status
 * Displays: synced, pending, syncing, or failed states
 */
@Composable
fun SyncStatusBadge(
    syncStatus: String,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val statusInfo = getSyncStatusInfo(syncStatus)

    Surface(
        modifier =
            modifier
                .then(
                    if (syncStatus == "failed" && onRetryClick != null) {
                        Modifier.clickable { onRetryClick() }
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(12.dp),
        color = statusInfo.backgroundColor,
        contentColor = statusInfo.textColor,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = if (compact) 6.dp else 8.dp,
                    vertical = if (compact) 4.dp else 6.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
            )

            if (!compact) {
                Text(
                    text = statusInfo.label,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // Show retry icon for failed status
            if (syncStatus == "failed" && onRetryClick != null) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                )
            }
        }
    }
}

/**
 * Banner showing offline/sync status at top of screen
 */
@Composable
fun SyncStatusBanner(
    isOnline: Boolean,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !isOnline || pendingSyncCount > 0,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color =
                if (!isOnline) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            contentColor =
                if (!isOnline) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector =
                            if (!isOnline) {
                                Icons.Default.CloudOff
                            } else {
                                Icons.Default.CloudQueue
                            },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                if (!isOnline) {
                                    "Offline"
                                } else {
                                    "$pendingSyncCount pending"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (!isOnline) {
                            Text(
                                text = "Changes will sync when online",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (isOnline && pendingSyncCount > 0) {
                    OutlinedButton(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        modifier = Modifier.height(36.dp),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync now",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync")
                    }
                }
            }
        }
    }
}

/**
 * Small syncing indicator for individual items
 */
@Composable
fun SyncingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

// Data class for sync status info
private data class SyncStatusInfo(
    val label: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val textColor: Color,
)

@Composable
private fun getSyncStatusInfo(syncStatus: String): SyncStatusInfo {
    return when (syncStatus.lowercase()) {
        "synced" ->
            SyncStatusInfo(
                label = "Synced",
                icon = Icons.Default.CloudDone,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        "pending" ->
            SyncStatusInfo(
                label = "Pending",
                icon = Icons.Default.CloudQueue,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        "syncing" ->
            SyncStatusInfo(
                label = "Syncing",
                icon = Icons.Default.CloudSync,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        "failed" ->
            SyncStatusInfo(
                label = "Failed",
                icon = Icons.Default.CloudOff,
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                textColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        else ->
            SyncStatusInfo(
                label = "Unknown",
                icon = Icons.Default.Help,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}
