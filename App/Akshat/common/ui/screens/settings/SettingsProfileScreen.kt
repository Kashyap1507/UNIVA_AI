package com.kashyap.wizlyai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.LocalBottomNavHeight
import com.kashyap.wizlyai.ui.components.CollapsibleTopBar
import com.kashyap.wizlyai.ui.components.decorative
import com.kashyap.wizlyai.ui.components.radioButtonRole
import com.kashyap.wizlyai.ui.components.switchRole
import com.kashyap.wizlyai.ui.components.textFieldSemantics
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.theme.ThemeMode
import com.kashyap.wizlyai.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Settings & Profile Screen - Redesigned to match Wizly-web
 * Light gradient background with card-based sections
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProfileScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToDeveloperSettings: () -> Unit,
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateToReferrals: () -> Unit = {},
    onNavigateToCurrentPlan: () -> Unit = {},
    onNavigateToPremiumPlans: () -> Unit = {},
    onSignOut: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    preferencesManager: com.kashyap.wizlyai.data.preferences.PreferencesManager? = null,
    scrollToNotifications: Boolean = false,
    showBackButton: Boolean = false, // true when Profile is not a tab (Master's: Research has the slot)
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

    val subscriptionViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.SubscriptionViewModel() }
    val studentProfileRepository = remember { com.kashyap.wizlyai.data.repository.StudentProfileRepository() }

    // State
    val userProfile by viewModel.userProfile.collectAsState()
    val studentProfile by UserSessionManager.instance.studentProfile.collectAsState()
    val pushNotifications by viewModel.pushNotifications.collectAsState()
    val practiceReminders by viewModel.practiceReminders.collectAsState()
    val currentSubscription by subscriptionViewModel.currentSubscriptionState.collectAsState()

    // Load data on first composition
    LaunchedEffect(Unit) {
        val userId = UserSessionManager.instance.userId
        if (userId != null) {
            viewModel.loadUserProfile(userId)
        }
        studentProfileRepository.getProfile()
        // Load settings from preferences
        viewModel.loadSettings()
        // Load subscription data
        subscriptionViewModel.loadCurrentSubscription()
    }

    // Scroll state for collapsing header
    val scrollState = rememberLazyListState()
    val isCollapsed =
        remember {
            derivedStateOf { scrollState.firstVisibleItemScrollOffset > 100 }
        }

    // Scroll to notifications section if requested
    LaunchedEffect(scrollToNotifications) {
        if (scrollToNotifications) {
            // Index 3 is the Notifications section (0: Profile, 1: Account Settings, 2: Theme, 3: Notifications)
            scrollState.animateScrollToItem(index = 3, scrollOffset = 0)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsibleTopBar(
                title = "Profile",
                isCollapsed = isCollapsed.value,
                showBackButton = showBackButton,
                onBackClick = onBack,
                collapsible = false, // Keep title visible on scroll for consistency with other tabs
            )

            SettingsContent(
                userProfile = userProfile,
                studentSummary = studentSummary(studentProfile),
                currentSubscription = currentSubscription,
                pushNotifications = pushNotifications,
                practiceReminders = practiceReminders,
                onEditProfile = onEditProfile,
                onNavigateToAppSettings = onNavigateToAppSettings,
                onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                onNavigateToDocuments = onNavigateToDocuments,
                onNavigateToNotificationSettings = onNavigateToNotificationSettings,
                onNavigateToReferrals = onNavigateToReferrals,
                onNavigateToCurrentPlan = onNavigateToCurrentPlan,
                onNavigateToPremiumPlans = onNavigateToPremiumPlans,
                onTogglePushNotifications = { viewModel.togglePushNotifications() },
                onTogglePracticeReminders = { viewModel.togglePracticeReminders() },
                onSignOut = {
                    UserSessionManager.instance.clearSession()
                    onSignOut()
                },
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                scrollState = scrollState,
            )
        }
    }
}

/** One-line study summary, e.g. "Master's · Applied Computing · University of Windsor". */
internal fun studentSummary(profile: com.kashyap.wizlyai.network.models.StudentProfile): String {
    val parts =
        listOfNotNull(
            profile.degree?.label,
            profile.program?.takeIf { it.isNotBlank() },
            profile.university?.takeIf { it.isNotBlank() },
        )
    val semester = profile.currentSemester?.let { " · Semester $it" }.orEmpty()
    return if (parts.isEmpty()) "Add your university details" else parts.joinToString(" · ") + semester
}

@Composable
private fun SettingsContent(
    userProfile: UiState<com.kashyap.wizlyai.data.models.User>,
    studentSummary: String,
    currentSubscription: com.kashyap.wizlyai.data.repository.CurrentSubscriptionState,
    pushNotifications: Boolean,
    practiceReminders: Boolean,
    onEditProfile: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToDeveloperSettings: () -> Unit,
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateToReferrals: () -> Unit = {},
    onNavigateToCurrentPlan: () -> Unit = {},
    onNavigateToPremiumPlans: () -> Unit = {},
    onTogglePushNotifications: () -> Unit,
    onTogglePracticeReminders: () -> Unit,
    onSignOut: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    scrollState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val bottomNavHeight = LocalBottomNavHeight.current
    val colorScheme = MaterialTheme.colorScheme
    val primaryTextColor = colorScheme.onSurface
    val secondaryTextColor = colorScheme.onSurfaceVariant
    val outlineColor = colorScheme.outlineVariant
    val cardColors = CardDefaults.cardColors(containerColor = colorScheme.surface)
    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = bottomNavHeight + 16.dp, // Space for bottom nav + breathing room
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Profile Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Avatar with gradient
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .background(
                                    brush =
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary,
                                                ),
                                        ),
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val userName =
                            when (userProfile) {
                                is UiState.Success -> userProfile.data.name
                                else -> "User"
                            }
                        Text(
                            text = userName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Profile info
                    Column(modifier = Modifier.weight(1f)) {
                        when (userProfile) {
                            is UiState.Success -> {
                                Text(
                                    text = userProfile.data.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = primaryTextColor,
                                )
                                Text(
                                    text = studentSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor,
                                )
                            }
                            is UiState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                            else -> {
                                Text("Loading...", color = secondaryTextColor)
                            }
                        }
                    }

                    // Edit button
                    OutlinedButton(
                        onClick = onEditProfile,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.primary,
                            ),
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Edit profile information"
                            },
                    ) {
                        Text("Edit")
                    }
                }
            }
        }

        // Account Section
        item {
            SettingsSection(
                title = "Account",
                icon = Icons.Default.Person,
            ) {
                SettingsItem(
                    title = "Study Profile",
                    description = "Name, university, program and semester",
                    onClick = onEditProfile,
                    showArrow = true,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "Invite Friends",
                    description = "Share your referral code and earn rewards",
                    onClick = onNavigateToReferrals,
                    showArrow = true,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "Email",
                    description =
                        when (userProfile) {
                            is UiState.Success -> userProfile.data.email
                            else -> "Loading..."
                        },
                    onClick = { },
                    showArrow = false,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "App Settings",
                    description = "Cloud sync and preferences",
                    onClick = onNavigateToAppSettings,
                    showArrow = true,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "Documents",
                    description = "View and manage all scanned documents",
                    onClick = onNavigateToDocuments,
                    showArrow = true,
                )
            }
        }

        // Subscription Section
        item {
            SettingsSection(
                title = "Subscription",
                icon = Icons.Default.Stars,
            ) {
                when (currentSubscription) {
                    is com.kashyap.wizlyai.data.repository.CurrentSubscriptionState.Success -> {
                        val subscriptionDetails = currentSubscription.data.subscription
                        val usage = currentSubscription.data.usage

                        val tierName = subscriptionDetails.tier.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase() else it.toString()
                        }
                        val planName = when (subscriptionDetails.tier) {
                            "free" -> "Free Plan"
                            "basic" -> "Basic Plan"
                            "premium" -> "Premium Plan"
                            "pro" -> "Pro Plan"
                            else -> "$tierName Plan"
                        }

                        // Build usage description
                        val usageDescription = if (usage.unlimited) {
                            "Unlimited access"
                        } else {
                            val usageParts = mutableListOf<String>()
                            usage.features["work_checks"]?.let { feature ->
                                usageParts.add("${feature.used}/${feature.limit} work checks")
                            }
                            usage.features["practice_sets"]?.let { feature ->
                                usageParts.add("${feature.used}/${feature.limit} practice sets")
                            }
                            if (usageParts.isNotEmpty()) {
                                usageParts.joinToString(" • ")
                            } else {
                                "No usage data"
                            }
                        }

                        val fullDescription = "$planName • ${subscriptionDetails.status.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase() else it.toString()
                        }}\n$usageDescription"

                        SettingsItem(
                            title = "Current Plan",
                            description = fullDescription,
                            onClick = onNavigateToCurrentPlan,
                            showArrow = true,
                        )
                    }
                    is com.kashyap.wizlyai.data.repository.CurrentSubscriptionState.Loading -> {
                        SettingsItem(
                            title = "Current Plan",
                            description = "Loading...",
                            onClick = onNavigateToCurrentPlan,
                            showArrow = true,
                        )
                    }
                    is com.kashyap.wizlyai.data.repository.CurrentSubscriptionState.Error -> {
                        SettingsItem(
                            title = "Current Plan",
                            description = "Tap to view details",
                            onClick = onNavigateToCurrentPlan,
                            showArrow = true,
                        )
                    }
                }
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "Manage Subscription",
                    description = "View plans, upgrade, or manage billing",
                    onClick = onNavigateToPremiumPlans,
                    showArrow = true,
                )
            }
        }

        // Appearance Section
        item {
            SettingsSection(
                title = "Appearance",
                icon = Icons.Default.DarkMode,
            ) {
                ThemeModeOptionRow(
                    title = "Auto",
                    description = "Match your device setting",
                    icon = Icons.Default.BrightnessAuto,
                    selected = themeMode == ThemeMode.Auto,
                    onClick = { onThemeModeSelected(ThemeMode.Auto) },
                )
                HorizontalDivider(color = outlineColor)
                ThemeModeOptionRow(
                    title = "Light",
                    description = "Always use the light experience",
                    icon = Icons.Default.WbSunny,
                    selected = themeMode == ThemeMode.Light,
                    onClick = { onThemeModeSelected(ThemeMode.Light) },
                )
                HorizontalDivider(color = outlineColor)
                ThemeModeOptionRow(
                    title = "Dark",
                    description = "Sleek, modern dark interface",
                    icon = Icons.Default.DarkMode,
                    selected = themeMode == ThemeMode.Dark,
                    onClick = { onThemeModeSelected(ThemeMode.Dark) },
                )
            }
        }

        // Notifications Section
        item {
            SettingsSection(
                title = "Notifications",
                icon = Icons.Default.Notifications,
            ) {
                SettingsItem(
                    title = "Set Notifications",
                    description = "Configure calendar event notification timing",
                    onClick = onNavigateToNotificationSettings,
                    showArrow = true,
                )
                HorizontalDivider(color = outlineColor)
                SettingsToggleItem(
                    title = "Push Notifications",
                    description = "Receive deadline reminders and study updates",
                    checked = pushNotifications,
                    onCheckedChange = { onTogglePushNotifications() },
                )
                HorizontalDivider(color = outlineColor)
                SettingsToggleItem(
                    title = "Practice Reminders",
                    description = "Get notified when it's time to practice",
                    checked = practiceReminders,
                    onCheckedChange = { onTogglePracticeReminders() },
                )
            }
        }

        // Support Section
        item {
            SettingsSection(
                title = "Support",
                icon = Icons.Default.Info,
            ) {
                SettingsItem(
                    title = "Help Center",
                    description = "Get answers to common questions",
                    onClick = { /* TODO */ },
                    showArrow = true,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "Contact Support",
                    description = "Reach out for help",
                    onClick = { /* TODO */ },
                    showArrow = true,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "App Version",
                    description = "v1.0.0",
                    onClick = { },
                    showArrow = false,
                )
                HorizontalDivider(color = outlineColor)
                SettingsItem(
                    title = "Developer Settings",
                    description = "Advanced debugging options",
                    onClick = onNavigateToDeveloperSettings,
                    showArrow = true,
                )
            }
        }

        // Sign Out Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSignOut)
                            .padding(16.dp)
                            .semantics {
                                contentDescription = "Sign out of your account"
                                role = Role.Button
                            },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.decorative(),
                    )
                    Text(
                        text = "Sign Out",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics { heading() },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).decorative(),
                    tint = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    showArrow: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
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
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.switchRole(checked, "$title: $description"),
        )
    }
}

@Composable
private fun ThemeModeOptionRow(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor =
        if (selected) {
            colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = colorScheme.primary,
                    unselectedColor = colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.radioButtonRole(selected, "$title theme: $description"),
        )
    }
}
