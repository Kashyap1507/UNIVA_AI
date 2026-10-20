package com.kashyap.wizlyai.ui.screens.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.data.repository.SubscriptionPlansState
import com.kashyap.wizlyai.network.models.SubscriptionPlan
import com.kashyap.wizlyai.ui.viewmodel.SubscriptionViewModel

/**
 * Premium Plans Screen
 * Shows all available subscription tiers with features and pricing
 *
 * Features:
 * - Material 3 design
 * - Popular plan highlighted
 * - Monthly/Yearly toggle
 * - Feature comparison
 * - Current plan indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPlansScreen(
    viewModel: SubscriptionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plansState by viewModel.plansState.collectAsState()
    val currentSubscriptionState by viewModel.currentSubscriptionState.collectAsState()
    val currentTier = when (val state = currentSubscriptionState) {
        is com.kashyap.wizlyai.data.repository.CurrentSubscriptionState.Success -> {
            state.data.subscription.tier
        }
        else -> "free"
    }
    val upgradeInProgress by viewModel.upgradeInProgress.collectAsState()
    val upgradeResult by viewModel.upgradeResult.collectAsState()

    var showYearlyPricing by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var selectedPlanForPreview by remember { mutableStateOf<String?>(null) }

    // Load plans and current subscription on first composition
    LaunchedEffect(Unit) {
        viewModel.loadPlans()
        viewModel.loadCurrentSubscription()
    }

    // Handle upgrade result
    LaunchedEffect(upgradeResult) {
        upgradeResult?.let { result ->
            if (result.isSuccess) {
                // Show success and navigate back
                onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium Plans") },
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
            when (val state = plansState) {
                is SubscriptionPlansState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SubscriptionPlansState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Failed to load plans",
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
                        Button(onClick = { viewModel.loadPlans(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }

                is SubscriptionPlansState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Choose Your Plan",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Unlock more work checks, practice sets and features",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Monthly/Yearly toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Monthly",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (!showYearlyPricing) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = showYearlyPricing,
                                        onCheckedChange = { showYearlyPricing = it }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Yearly",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (showYearlyPricing) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        if (showYearlyPricing) {
                                            Text(
                                                text = "Save ~17%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Plan cards
                        items(state.data.plans) { plan ->
                            PlanCard(
                                plan = plan,
                                isCurrentPlan = plan.tier == currentTier,
                                isSelected = selectedPlanForPreview == plan.tier,
                                showYearlyPrice = showYearlyPricing,
                                onClick = {
                                    selectedPlanForPreview = if (selectedPlanForPreview == plan.tier) null else plan.tier
                                },
                                onSelectPlan = { selectedPlan = plan },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Selected plan confirmation dialog
                    selectedPlan?.let { plan ->
                        AlertDialog(
                            onDismissRequest = { selectedPlan = null },
                            title = { Text("Upgrade to ${plan.name}?") },
                            text = {
                                Column {
                                    val price = if (showYearlyPricing) plan.priceYearly else plan.priceMonthly
                                    val period = if (showYearlyPricing) "year" else "month"
                                    Text("You'll be charged $${price}/$period")
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.upgradePlan(plan.tier, autoRenew = true)
                                        selectedPlan = null
                                    },
                                    enabled = !upgradeInProgress
                                ) {
                                    if (upgradeInProgress) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Confirm")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { selectedPlan = null },
                                    enabled = !upgradeInProgress
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual plan card component
 */
@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isCurrentPlan: Boolean,
    isSelected: Boolean,
    showYearlyPrice: Boolean,
    onClick: () -> Unit,
    onSelectPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val price = if (showYearlyPrice) plan.priceYearly else plan.priceMonthly
    val period = if (showYearlyPrice) "year" else "month"
    val isFree = plan.tier == "free"

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isCurrentPlan -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (plan.isPopular) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Popular",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else if (isCurrentPlan) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Current",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }

            // Description
            Text(
                text = plan.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Price
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isFree) "Free" else "$${price}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!isFree) {
                    Text(
                        text = "/ $period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Features list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                plan.features.forEach { feature ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Action button - only show when selected
            AnimatedVisibility(
                visible = isSelected && !isCurrentPlan
            ) {
                Button(
                    onClick = onSelectPlan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isFree) "Downgrade" else "Select Plan",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
