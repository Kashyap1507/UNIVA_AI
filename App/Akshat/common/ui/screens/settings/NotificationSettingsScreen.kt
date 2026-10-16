package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.data.models.NotificationSettings
import com.kashyap.wizlyai.data.models.TimeOfDay
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.data.preferences.getNotificationSettings
import com.kashyap.wizlyai.data.preferences.saveNotificationSettings
import com.kashyap.wizlyai.data.repository.DeadlineRepository
import com.kashyap.wizlyai.deadlines.DeadlineLogic
import com.kashyap.wizlyai.notifications.CalendarNotificationManagerBridge
import com.kashyap.wizlyai.ui.LocalBottomNavHeight
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.viewmodel.SettingsViewModel
import com.kashyap.wizlyai.util.AccessibilityUtils.switchRole
import kotlinx.coroutines.launch

/**
 * Notification Settings Screen
 * Configure deadline reminder timing and preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    preferencesManager: PreferencesManager?,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel =
        remember(preferencesManager) {
            SettingsViewModel(
                preferencesManager = preferencesManager,
                viewModelScope = coroutineScope,
            )
        }
    val deadlineRepository = remember { DeadlineRepository() }

    // Load current settings
    var settings by remember { mutableStateOf(preferencesManager?.getNotificationSettings() ?: NotificationSettings.DEFAULT) }
    val pushNotifications by viewModel.pushNotifications.collectAsState()
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadSettings() }

    // Scroll state for collapsing header
    val scrollState = rememberLazyListState()
    val isCollapsed =
        remember {
            derivedStateOf { scrollState.firstVisibleItemScrollOffset > 100 }
        }

    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val backgroundBrush =
        remember(isDarkTheme, colorScheme) {
            if (isDarkTheme) {
                Brush.linearGradient(
                    colors =
                        listOf(
                            colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            colorScheme.background,
                        ),
                )
            } else {
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color(0xFFEFF6FF),
                            Color(0xFFF0FDF4),
                        ),
                )
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Collapsing Top Bar
            CollapsibleTopBar(
                title = "Notification Settings",
                isCollapsed = isCollapsed.value,
                showBackButton = true,
                onBackClick = onBack,
                trailingContent = {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton

                            coroutineScope.launch {
                                isSaving = true
                                try {
                                    // Step 1: Save new settings
                                    preferencesManager?.saveNotificationSettings(settings)

                                    // Step 2: Cancel all existing notifications
                                    CalendarNotificationManagerBridge.cancelAllNotifications()

                                    // Step 3: Reschedule reminders for open deadlines
                                    if (settings.enableCalendarNotifications && preferencesManager != null) {
                                        deadlineRepository.list().fold(
                                            onSuccess = { deadlines ->
                                                deadlines.mapNotNull { DeadlineLogic.toReminderEvent(it) }.forEach { event ->
                                                    try {
                                                        CalendarNotificationManagerBridge.scheduleNotifications(
                                                            event = event,
                                                            preferencesManager = preferencesManager,
                                                        )
                                                    } catch (e: Exception) {
                                                        println("⚠️ Failed to schedule a reminder for ${event.title}: ${e.message}")
                                                    }
                                                }
                                            },
                                            onFailure = { error -> println("⚠️ Couldn't load deadlines for reminders: ${error.message}") },
                                        )
                                    }
                                } catch (e: Exception) {
                                    println("❌ Error saving settings: ${e.message}")
                                } finally {
                                    isSaving = false
                                    onBack()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (isSaving) {
                                        "Saving notification settings, please wait"
                                    } else {
                                        "Save notification settings and apply changes"
                                    }
                            },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save", color = colorScheme.primary)
                        }
                    }
                },
            )

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = LocalBottomNavHeight.current + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (settings.enableCalendarNotifications) {
                    // Notification Range Section
                    item {
                        NotificationRangeCard(
                            startDaysBefore = settings.startDaysBefore,
                            stopHoursBefore = settings.stopHoursBefore,
                            onStartDaysChange = { settings = settings.copy(startDaysBefore = it) },
                            onStopHoursChange = { settings = settings.copy(stopHoursBefore = it) },
                        )
                    }

                    // Daily Notification Time
                    item {
                        DailyNotificationTimeCard(
                            timeOfDay = settings.dailyNotificationTime,
                            onTimeChange = { settings = settings.copy(dailyNotificationTime = it) },
                        )
                    }

                    // Hourly Notifications (Event Day)
                    item {
                        HourlyNotificationsCard(
                            hourlyStartHoursBefore = settings.hourlyStartHoursBefore,
                            hourlyIntervalHours = settings.hourlyIntervalHours,
                            stopHoursBefore = settings.stopHoursBefore,
                            onStartHoursChange = { settings = settings.copy(hourlyStartHoursBefore = it) },
                            onIntervalChange = { settings = settings.copy(hourlyIntervalHours = it) },
                        )
                    }

                }
            }
        }

        // Disabled Overlay - shown when push notifications are OFF
        if (!pushNotifications) {
            DisabledOverlay(
                onTurnOn = onNavigateToProfile,
            )
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MasterEnableCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Calendar event notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Get reminded about upcoming exams, quizzes and assignments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null, // Read-only
                enabled = false, // Disabled to show it's controlled elsewhere
            )
        }
    }
}

@Composable
private fun NotificationRangeCard(
    startDaysBefore: Int,
    stopHoursBefore: Int,
    onStartDaysChange: (Int) -> Unit,
    onStopHoursChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Notification Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Set when notifications start and stop",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Start From Picker
                NumberPicker(
                    label = "Start From",
                    value = startDaysBefore,
                    range = 1..30,
                    unit = if (startDaysBefore == 1) "day" else "days",
                    subtitle = "before",
                    onValueChange = onStartDaysChange,
                )

                // Arrow
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                // Stop At Picker
                NumberPicker(
                    label = "Stop At",
                    value = stopHoursBefore,
                    range = 1..24,
                    unit = if (stopHoursBefore == 1) "hour" else "hours",
                    subtitle = "before",
                    onValueChange = onStopHoursChange,
                )
            }
        }
    }
}

@Composable
private fun NumberPicker(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    subtitle: String,
    onValueChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = { if (value > range.first) onValueChange(value - 1) },
                    modifier =
                        Modifier
                            .size(32.dp)
                            .semantics {
                                contentDescription = "Decrease $label to ${value - 1} $unit $subtitle"
                            },
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(60.dp),
                ) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                IconButton(
                    onClick = { if (value < range.last) onValueChange(value + 1) },
                    modifier =
                        Modifier
                            .size(32.dp)
                            .semantics {
                                contentDescription = "Increase $label to ${value + 1} $unit $subtitle"
                            },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyNotificationTimeCard(
    timeOfDay: TimeOfDay,
    onTimeChange: (TimeOfDay) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Daily Notification Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Time to receive daily countdown notifications",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time display and picker (simplified - just showing current time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(8.dp),
                ) {
                    Text(
                        text = timeOfDay.format12Hour(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            // Hour and Minute pickers
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Hour picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val newHour = if (timeOfDay.hour == 23) 0 else timeOfDay.hour + 1
                        onTimeChange(timeOfDay.copy(hour = newHour))
                    }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase hour")
                    }
                    Text(
                        text = timeOfDay.hour.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = {
                        val newHour = if (timeOfDay.hour == 0) 23 else timeOfDay.hour - 1
                        onTimeChange(timeOfDay.copy(hour = newHour))
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease hour")
                    }
                    Text("Hour", style = MaterialTheme.typography.labelSmall)
                }

                Text(":", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 32.dp))

                // Minute picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val newMinute = if (timeOfDay.minute >= 55) 0 else ((timeOfDay.minute / 5) * 5) + 5
                        onTimeChange(timeOfDay.copy(minute = newMinute))
                    }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase minute")
                    }
                    Text(
                        text = timeOfDay.minute.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = {
                        val newMinute = if (timeOfDay.minute <= 0) 55 else ((timeOfDay.minute - 1) / 5) * 5
                        onTimeChange(timeOfDay.copy(minute = newMinute))
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease minute")
                    }
                    Text("Minute", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HourlyNotificationsCard(
    hourlyStartHoursBefore: Int,
    hourlyIntervalHours: Int,
    stopHoursBefore: Int,
    onStartHoursChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Hourly Notifications (Event Day)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Get reminders every few hours on event day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Start hours before slider
            Text(
                "Start hourly reminders:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$hourlyStartHoursBefore ${if (hourlyStartHoursBefore == 1) "hour" else "hours"} before event",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = hourlyStartHoursBefore.toFloat(),
                onValueChange = { onStartHoursChange(it.toInt()) },
                valueRange = (stopHoursBefore + 1f)..24f,
                steps = 23 - stopHoursBefore - 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Start hourly reminders: $hourlyStartHoursBefore hours before event"
                        },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Interval slider
            Text(
                "Send reminder every:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$hourlyIntervalHours ${if (hourlyIntervalHours == 1) "hour" else "hours"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = hourlyIntervalHours.toFloat(),
                onValueChange = { onIntervalChange(it.toInt()) },
                valueRange = 1f..12f,
                steps = 11,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Send reminder every: $hourlyIntervalHours hours"
                        },
            )
        }
    }
}

/**
 * Overlay shown when push notifications are disabled
 * Prompts user to enable notifications in Profile
 */
@Composable
private fun DisabledOverlay(onTurnOn: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.85f))
                .clickable(enabled = false) { },
        // Intercept clicks
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // Icon
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = "Turn on Notifications",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Subtitle
            Text(
                text = "Enable push notifications in your profile to configure notification settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Turn On Button
            Button(
                onClick = onTurnOn,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(48.dp)
                        .semantics {
                            contentDescription = "Navigate to profile settings to enable push notifications"
                        },
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Turn On",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
