package com.kashyap.wizlyai.ui.screens.subscription

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.data.repository.CurrentSubscriptionState
import com.kashyap.wizlyai.ui.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Current Plan Details Screen
 * Shows user's current subscription plan, usage statistics, and limits
 *
 * Features:
 * - Plan tier and status display
 * - Usage statistics with progress bars
 * - Daily reset countdown
 * - Plan dates and auto-renewal info
 * - Upgrade button to Premium Plans
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentPlanScreen(
    viewModel: SubscriptionViewModel,
    onNavigateBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSubscription by viewModel.currentSubscriptionState.collectAsState()

    // Countdown timer state
    var timeUntilReset by remember { mutableStateOf(viewModel.getTimeUntilReset()) }

    // Load subscription data on first composition
    LaunchedEffect(Unit) {
        viewModel.loadCurrentSubscription()
    }

    // Update countdown every minute
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000) // Update every minute
            timeUntilReset = viewModel.getTimeUntilReset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Current Plan") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = currentSubscription) {
                is CurrentSubscriptionState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is CurrentSubscriptionState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Failed to load subscription",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCurrentSubscription(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }

                is CurrentSubscriptionState.Success -> {
                    val subscription = state.data.subscription
                    val usage = state.data.usage

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Plan Header Card
                        item {
                            PlanHeaderCard(
                                tier = subscription.tier,
                                status = subscription.status,
                                planName = subscription.planName
                            )
                        }

                        // Account Type Card
                        item {
                            AccountTypeCard(
                                accountType = subscription.accountType,
                                unlimited = usage.unlimited
                            )
                        }

                        // Usage Statistics Card
                        if (!usage.unlimited) {
                            item {
                                UsageStatisticsCard(
                                    features = usage.features,
                                    hoursUntilReset = timeUntilReset["hours"] ?: 0,
                                    minutesUntilReset = timeUntilReset["minutes"] ?: 0
                                )
                            }
                        }

                        // Plan Details Card
                        item {
                            PlanDetailsCard(
                                limits = subscription.planLimits,
                                startDate = subscription.startDate,
                                endDate = subscription.endDate,
                                autoRenew = subscription.autoRenew
                            )
                        }

                        // Enhanced Upgrade Button
                        if (subscription.tier != "pro") {
                            item {
                                Button(
                                    onClick = onUpgrade,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFF6366F1),
                                                        Color(0xFF8B5CF6),
                                                        Color(0xFFA855F7)
                                                    )
                                                )
                                            )
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Upgrade,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = Color.White
                                            )
                                            Text(
                                                text = "Upgrade Plan",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plan header card showing tier, status, and icon with gradient background
 */
@Composable
private fun PlanHeaderCard(
    tier: String,
    status: String,
    planName: String,
    modifier: Modifier = Modifier,
) {
    val tierIcon = when (tier) {
        "free" -> Icons.Default.CardMembership
        "basic" -> Icons.Default.Star
        "premium" -> Icons.Default.Stars
        "pro" -> Icons.Default.WorkspacePremium
        else -> Icons.Default.CardMembership
    }

    val gradientColors = when (tier) {
        "free" -> listOf(
            Color(0xFFE3F2FD),
            Color(0xFFBBDEFB)
        )
        "basic" -> listOf(
            Color(0xFFFFF3E0),
            Color(0xFFFFE0B2)
        )
        "premium" -> listOf(
            Color(0xFFF3E5F5),
            Color(0xFFE1BEE7)
        )
        "pro" -> listOf(
            Color(0xFFE8F5E9),
            Color(0xFFA5D6A7),
            Color(0xFF81C784)
        )
        else -> listOf(
            Color(0xFFE3F2FD),
            Color(0xFFBBDEFB)
        )
    }

    val iconBackgroundColor = when (tier) {
        "free" -> Color(0xFF2196F3)
        "basic" -> Color(0xFFFF9800)
        "premium" -> Color(0xFF9C27B0)
        "pro" -> Color(0xFF4CAF50)
        else -> Color(0xFF2196F3)
    }

    // Pulse animation for status badge
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Icon with circular background
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(iconBackgroundColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tierIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = iconBackgroundColor
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = planName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = Color(0xFF1A237E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Status badge with pulse animation
                    Surface(
                        color = when (status.lowercase()) {
                            "active" -> Color(0xFF4CAF50)
                            "expired" -> MaterialTheme.colorScheme.error
                            "cancelled" -> Color(0xFF757575)
                            else -> Color(0xFF757575)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                    ) {
                        Text(
                            text = status.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase() else it.toString()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Account type card showing full access or referral limited
 */
@Composable
private fun AccountTypeCard(
    accountType: String,
    unlimited: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon with gradient background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (accountType == "full") {
                                listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                            } else {
                                listOf(Color(0xFF2196F3), Color(0xFF64B5F6))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (accountType == "full") Icons.Default.VerifiedUser else Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Account Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (accountType == "full") {
                        if (unlimited) "Full Access • Unlimited" else "Full Access"
                    } else {
                        "Referral Limited Access"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Usage statistics card with circular progress indicators
 */
@Composable
private fun UsageStatisticsCard(
    features: Map<String, com.kashyap.wizlyai.network.models.FeatureUsage>,
    hoursUntilReset: Int,
    minutesUntilReset: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enhanced header with gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.1f),
                                Color(0xFFA855F7).copy(alpha = 0.1f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Daily Usage",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Resets in ${hoursUntilReset}h ${minutesUntilReset}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Usage items with spacing
            features.forEach { (featureName, usage) ->
                UsageItem(
                    featureName = featureName,
                    used = usage.used,
                    limit = usage.limit,
                    percentage = usage.percentage
                )
            }
        }
    }
}

/**
 * Individual usage item with circular progress indicator
 */
@Composable
private fun UsageItem(
    featureName: String,
    used: Int,
    limit: Int,
    percentage: Int,
    modifier: Modifier = Modifier,
) {
    val displayName = when (featureName) {
        "work_checks" -> "Work checks"
        "practice_sets" -> "Practice sets"
        "flashcard_sets" -> "AI flashcard sets"
        else -> featureName.replace("_", " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    val icon = when (featureName) {
        "work_checks" -> Icons.Default.Scanner
        "practice_sets" -> Icons.Default.Description
        "flashcard_sets" -> Icons.Default.AutoStories
        else -> Icons.Default.CheckCircle
    }

    val progressColor = when {
        percentage >= 90 -> Color(0xFFE53935)
        percentage >= 70 -> Color(0xFFFB8C00)
        else -> Color(0xFF43A047)
    }

    val iconBackgroundColor = when (featureName) {
        "work_checks" -> Color(0xFF2196F3)
        "practice_sets" -> Color(0xFF9C27B0)
        "flashcard_sets" -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circular progress with icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp)
            ) {
                // Background circle
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 6.dp,
                    trackColor = Color.Transparent
                )

                // Actual progress
                CircularProgressIndicator(
                    progress = { (percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(64.dp),
                    color = progressColor,
                    strokeWidth = 6.dp,
                    trackColor = Color.Transparent
                )

                // Icon in center
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBackgroundColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconBackgroundColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$used / $limit",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = progressColor
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${100 - percentage}% left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Plan details card showing limits and dates with enhanced design
 */
@Composable
private fun PlanDetailsCard(
    limits: Map<String, Int>,
    startDate: String?,
    endDate: String?,
    autoRenew: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enhanced header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF34D399))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Plan Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Plan limits section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Daily Limits",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    limits.forEach { (feature, limit) ->
                        val displayName = when (feature) {
                            "work_checks" -> "Work checks"
                            "practice_sets" -> "Practice sets"
                            "flashcard_sets" -> "AI flashcard sets"
                            else -> feature.replace("_", " ").replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase() else it.toString()
                            }
                        }

                        val icon = when (feature) {
                            "work_checks" -> Icons.Default.Scanner
                            "practice_sets" -> Icons.Default.Description
                            "flashcard_sets" -> Icons.Default.AutoStories
                            else -> Icons.Default.CheckCircle
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                color = if (limit == -1) {
                                    Color(0xFF10B981).copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (limit == -1) "Unlimited" else "$limit / day",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (limit == -1) {
                                        Color(0xFF10B981)
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dates section if available
            if (startDate != null || endDate != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    startDate?.let {
                        EnhancedDetailRow(
                            icon = Icons.Default.CalendarToday,
                            label = "Start Date",
                            value = formatDate(it)
                        )
                    }

                    endDate?.let {
                        EnhancedDetailRow(
                            icon = Icons.Default.Event,
                            label = "End Date",
                            value = formatDate(it)
                        )
                    }

                    EnhancedDetailRow(
                        icon = if (autoRenew) Icons.Default.Autorenew else Icons.Default.Close,
                        label = "Auto Renewal",
                        value = if (autoRenew) "Enabled" else "Disabled",
                        valueColor = if (autoRenew) Color(0xFF10B981) else Color(0xFF757575)
                    )
                }
            }
        }
    }
}

/**
 * Enhanced detail row with icon
 */
@Composable
private fun EnhancedDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}


/**
 * Format ISO date string to readable format
 */
private fun formatDate(isoDate: String): String {
    return try {
        // Simple formatting - extract date part
        val datePart = isoDate.substringBefore('T')
        datePart
    } catch (e: Exception) {
        isoDate
    }
}
