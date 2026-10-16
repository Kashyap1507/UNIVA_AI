package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.data.preferences.PreferenceKeys
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.network.NetworkConstants
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.util.AccessibilityUtils.decorative
import com.kashyap.wizlyai.util.AccessibilityUtils.switchRole

/**
 * Developer Settings Screen
 * Advanced debugging options for developers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    onBack: () -> Unit,
    preferencesManager: PreferencesManager,
    onRestartApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // State
    var showDebugInfoOverlay by remember {
        mutableStateOf(
            preferencesManager.getBoolean(
                PreferenceKeys.SHOW_DEBUG_INFO_OVERLAY,
                defaultValue = false,
            ),
        )
    }

    var useProductionServer by remember {
        mutableStateOf(
            preferencesManager.getBoolean(
                PreferenceKeys.USE_PRODUCTION_SERVER,
                defaultValue = NetworkConstants.defaultUseProductionServer(),
            ),
        )
    }

    var customServerIP by remember {
        mutableStateOf(
            preferencesManager.getString(
                PreferenceKeys.CUSTOM_SERVER_IP,
                defaultValue = "192.168.0.78:5000",
            ),
        )
    }

    // Track original IP to detect actual changes
    val originalCustomIP = remember {
        preferencesManager.getString(
            PreferenceKeys.CUSTOM_SERVER_IP,
            defaultValue = "192.168.0.78:5000",
        )
    }

    // Get current API URL for display based on toggle state
    val currentApiUrl = remember(useProductionServer, customServerIP) {
        val protocol = if (useProductionServer) "https" else "http"
        val baseUrl = if (useProductionServer) {
            NetworkConstants.BaseUrls.PRODUCTION // TODO: set real host in NetworkConstants
        } else {
            customServerIP.ifEmpty { "192.168.0.78:5000" }
        }
        "$protocol://$baseUrl/api"
    }

    // Scroll state for collapsing header
    val scrollState = rememberLazyListState()
    val isCollapsed =
        remember {
            derivedStateOf { scrollState.firstVisibleItemScrollOffset > 100 }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Collapsing Top Bar
            CollapsibleTopBar(
                title = "Developer Settings",
                isCollapsed = isCollapsed.value,
                showBackButton = true,
                onBackClick = onBack,
            )

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
                // Warning Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .decorative(),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Developer Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "These settings are for debugging and development purposes only.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }

                // Network Configuration Settings
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp).decorative(),
                                )
                                Text(
                                    text = "Network Configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Production Server Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Use Production Server",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (useProductionServer) "Connected to production" else "Using custom server",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Switch(
                                    checked = useProductionServer,
                                    onCheckedChange = { newValue ->
                                        useProductionServer = newValue
                                        preferencesManager.putBoolean(
                                            PreferenceKeys.USE_PRODUCTION_SERVER,
                                            newValue,
                                        )
                                    },
                                    modifier =
                                        Modifier.switchRole(
                                            enabled = useProductionServer,
                                            label = "Toggle production server mode",
                                        ),
                                )
                            }

                            // Custom Server IP TextField (only visible when production is OFF)
                            if (!useProductionServer) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Custom Server IP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = customServerIP,
                                    onValueChange = { newValue ->
                                        customServerIP = newValue
                                        // Save immediately for persistence
                                        preferencesManager.putString(
                                            PreferenceKeys.CUSTOM_SERVER_IP,
                                            newValue,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("IP:PORT") },
                                    placeholder = { Text("192.168.0.78:5000") },
                                    supportingText = {
                                        Text(
                                            "Android Emulator: 10.0.2.2:5000\n" +
                                                "Local Network: Your machine's IP address",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Done,
                                        ),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        ),
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )

                            // Current API URL Display
                            InfoRow(label = "Current API URL", value = currentApiUrl)

                            // Restart App Button
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRestartApp,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restart App to Apply Changes")
                            }
                        }
                    }
                }

                // Edge Detection Settings
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Edge Detection Debugging",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Debug Overlay Toggle
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show Debug Info Overlay",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Display edge detection coordinates, confidence, and processing time during photo review",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Switch(
                                    checked = showDebugInfoOverlay,
                                    onCheckedChange = { newValue ->
                                        showDebugInfoOverlay = newValue
                                        preferencesManager.putBoolean(
                                            PreferenceKeys.SHOW_DEBUG_INFO_OVERLAY,
                                            newValue,
                                        )
                                    },
                                    modifier =
                                        Modifier.switchRole(
                                            enabled = showDebugInfoOverlay,
                                            label = "Show debug information overlay during edge detection",
                                        ),
                                )
                            }
                        }
                    }
                }

                // Debug Info Description
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Debug Overlay Shows:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                "• Detection confidence (HIGH/MEDIUM/LOW/FAILED)",
                                "• Edge coordinates (TL, TR, BR, BL)",
                                "• Image and view dimensions",
                                "• Detection mode (AUTO/MANUAL)",
                                "• Processing status and errors",
                            ).forEach { item ->
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                // App Info
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Build Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            InfoRow(label = "Version", value = "1.0.0")
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            InfoRow(label = "Build Type", value = "Debug")
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            InfoRow(label = "OpenCV", value = "4.9.0")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
