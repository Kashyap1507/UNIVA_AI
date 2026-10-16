package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.data.models.ScanQuality
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.viewmodel.AppSettingsViewModel

/**
 * Enhanced App Settings Screen - Redesigned to match Profile tab theme
 *
 * Features:
 * - Collapsing top bar with gradient background
 * - Card-based sections matching Wizly-web design
 * - Sync settings (Cloud Sync, Cellular Sync, Sync Now)
 * - Scan Quality settings with visual slider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val cloudSyncEnabled by viewModel.cloudSyncEnabled.collectAsState()
    val cellularSyncEnabled by viewModel.cellularSyncEnabled.collectAsState()
    val scanQuality by viewModel.scanQuality.collectAsState()
    val isTogglingCloudSync by viewModel.isTogglingCloudSync.collectAsState()
    val isTogglingCellularSync by viewModel.isTogglingCellularSync.collectAsState()
    val isUpdatingScanQuality by viewModel.isUpdatingScanQuality.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()

    var showSyncNowDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Load settings on first composition
    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    // Handle sync result
    LaunchedEffect(syncResult) {
        when (syncResult) {
            is UiState.Success -> {
                snackbarHostState.showSnackbar(
                    message = (syncResult as UiState.Success<String>).data,
                    duration = SnackbarDuration.Short,
                )
                viewModel.clearSyncResult()
            }
            is UiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (syncResult as UiState.Error).message,
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearSyncResult()
            }
            else -> { /* Idle or Loading - do nothing */ }
        }
    }

    // Scroll state for collapsing header
    val scrollState = rememberLazyListState()
    val isCollapsed =
        remember {
            derivedStateOf { scrollState.firstVisibleItemScrollOffset > 100 }
        }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Collapsing Top Bar
                CollapsibleTopBar(
                    title = "App Settings",
                    isCollapsed = isCollapsed.value,
                    showBackButton = true,
                    onBackClick = onNavigateBack,
                )

                when (val state = settings) {
                    is UiState.Loading -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .loadingSemantics("Loading settings"),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Success -> {
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 80.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // SYNC SECTION
                            item {
                                SyncSection(
                                    cloudSyncEnabled = cloudSyncEnabled,
                                    cellularSyncEnabled = cellularSyncEnabled,
                                    isTogglingCloudSync = isTogglingCloudSync,
                                    isTogglingCellularSync = isTogglingCellularSync,
                                    onToggleCloudSync = { viewModel.toggleCloudSync() },
                                    onToggleCellularSync = { viewModel.toggleCellularSync() },
                                    onSyncNowClick = { showSyncNowDialog = true },
                                )
                            }

                            // SCAN SECTION
                            item {
                                ScanSection(
                                    scanQuality = scanQuality,
                                    isUpdating = isUpdatingScanQuality,
                                    onQualityChange = { viewModel.setScanQuality(it) },
                                )
                            }
                        }
                    }

                    is UiState.Error -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .errorSemantics(state.message),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp).size(48.dp),
                            )
                            Text(
                                text = "Failed to load settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 24.dp),
                            )
                            Button(
                                onClick = { viewModel.loadSettings() },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                            ) {
                                Text("Retry")
                            }
                        }
                    }

                    is UiState.Idle -> {
                        // Initial state - show nothing
                    }
                }
            }

            // Loading overlay for sync operation
            if (isSyncing) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Syncing...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }

    // Sync Now Confirmation Dialog
    if (showSyncNowDialog) {
        SyncNowDialog(
            cloudSyncEnabled = cloudSyncEnabled,
            onConfirm = {
                showSyncNowDialog = false
                if (!cloudSyncEnabled) {
                    viewModel.setCloudSync(true)
                }
                // Trigger immediate sync
                viewModel.triggerImmediateSync()
            },
            onDismiss = { showSyncNowDialog = false },
        )
    }
}

/**
 * Sync Settings Section - Matches Profile tab card design
 */
@Composable
private fun SyncSection(
    cloudSyncEnabled: Boolean,
    cellularSyncEnabled: Boolean,
    isTogglingCloudSync: Boolean,
    isTogglingCellularSync: Boolean,
    onToggleCloudSync: () -> Unit,
    onToggleCellularSync: () -> Unit,
    onSyncNowClick: () -> Unit,
) {
    SettingsSection(
        title = "Sync",
        icon = Icons.Default.Cloud,
    ) {
        // Cloud Sync Toggle
        SettingsToggleItem(
            title = "Cloud Sync",
            description =
                if (cloudSyncEnabled) {
                    "Files are synced to the cloud. You can access them from any device."
                } else {
                    "Files are stored only on this device. Without cloud sync, scanned documents cannot be recovered if deleted, or if the app is uninstalled. Documents will not sync to other devices."
                },
            checked = cloudSyncEnabled,
            onCheckedChange = { onToggleCloudSync() },
            enabled = !isTogglingCloudSync,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Cellular Data Sync Toggle
        SettingsToggleItem(
            title = "Use cellular data to sync files",
            description =
                if (!cloudSyncEnabled) {
                    "Enable Cloud Sync first to use this feature"
                } else if (cellularSyncEnabled) {
                    "Files sync over both WiFi and cellular data"
                } else {
                    "Files only sync when connected to WiFi"
                },
            checked = cellularSyncEnabled && cloudSyncEnabled,
            onCheckedChange = { onToggleCellularSync() },
            enabled = cloudSyncEnabled && !isTogglingCellularSync,
            dimmed = !cloudSyncEnabled,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Sync Now Button
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            ElevatedGlowButton(
                text = "Sync Now",
                onClick = onSyncNowClick,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).decorative(),
                    )
                },
                glowColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}

/**
 * Scan Quality Settings Section - Matches Profile tab card design
 */
@Composable
private fun ScanSection(
    scanQuality: ScanQuality,
    isUpdating: Boolean,
    onQualityChange: (ScanQuality) -> Unit,
) {
    SettingsSection(
        title = "Scan",
        icon = Icons.Default.CameraAlt,
    ) {
        // Section description
        Text(
            text = "Scan Quality & File Size",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Visual Document Icon with Dimensions
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp).decorative(),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = scanQuality.getDimensionString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = scanQuality.getFileSizeString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quality Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QualityLabel("Small", scanQuality == ScanQuality.SMALL)
            QualityLabel("Standard", scanQuality == ScanQuality.STANDARD)
            QualityLabel("Large", scanQuality == ScanQuality.LARGE)
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityLabel("HD", scanQuality == ScanQuality.HD)
                if (scanQuality == ScanQuality.HD) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quality Slider
        Slider(
            value = ScanQuality.getSliderPosition(scanQuality).toFloat(),
            onValueChange = { newValue ->
                val newQuality = ScanQuality.fromSliderPosition(newValue.toInt())
                if (newQuality != scanQuality) {
                    onQualityChange(newQuality)
                }
            },
            valueRange = 0f..3f,
            steps = 2,
            enabled = !isUpdating,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Premium Badge for HD
        if (scanQuality.isPremium) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    )
                    Text(
                        text = "Premium quality for professional documents and archival",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

/**
 * Settings Section Wrapper - Matches Profile tab design
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Section Header with Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).decorative(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heading(),
                )
            }

            // Section Content
            content()
        }
    }
}

/**
 * Settings Toggle Item - Matches Profile tab design
 */
@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (dimmed) 0.5f else 1f)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            enabled = enabled,
            modifier = Modifier.switchRole(checked, title),
        )
    }
}

/**
 * Quality Label for Slider
 */
@Composable
private fun QualityLabel(
    text: String,
    isSelected: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = if (isSelected) 12.sp else 11.sp,
    )
}

/**
 * Sync Now Confirmation Dialog
 */
@Composable
private fun SyncNowDialog(
    cloudSyncEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sync Now",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                if (!cloudSyncEnabled) {
                    Text(
                        text = "Cloud Sync is currently disabled. Confirming will:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text(
                        text = "• Enable Cloud Sync",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    Text(
                        text = "• Start syncing your documents immediately",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(
                        text = "This will sync all pending changes to the cloud immediately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
