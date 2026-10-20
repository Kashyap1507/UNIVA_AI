package com.kashyap.wizlyai.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.network.models.ReferredUser
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.ui.theme.BluePrimary
import com.kashyap.wizlyai.ui.viewmodel.ReferralViewModel

/**
 * Referral Screen (Invite Friends)
 * Displays referral code, stats, share options, and list of referred users
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { ReferralViewModel() }
    val referralCodeState by viewModel.referralCodeState.collectAsState()
    val referralStatsState by viewModel.referralStatsState.collectAsState()
    val copyFeedback by viewModel.copyFeedback.collectAsState()

    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite Friends", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            when {
                referralCodeState is UiState.Loading && referralStatsState is UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                referralCodeState is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = (referralCodeState as UiState.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
                referralCodeState is UiState.Success -> {
                    val codeData = (referralCodeState as UiState.Success).data
                    val statsData = (referralStatsState as? UiState.Success)?.data

                    ReferralContent(
                        referralCode = codeData.referralCode,
                        referralLimit = codeData.referralLimit,
                        referralsRemaining = codeData.referralsRemaining,
                        canRefer = codeData.canRefer,
                        referredUsers = statsData?.referredUsers ?: emptyList(),
                        isLoadingStats = referralStatsState is UiState.Loading,
                        copyFeedback = copyFeedback,
                        onCopyCode = { viewModel.copyReferralCode(codeData.referralCode) },
                        onShareCode = { viewModel.shareReferralCode(codeData.referralCode) },
                        onRefresh = { viewModel.refresh() }
                    )
                }
                else -> {
                    // Idle state - shouldn't normally reach here
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferralContent(
    referralCode: String,
    referralLimit: Int,
    referralsRemaining: Int,
    canRefer: Boolean,
    referredUsers: List<ReferredUser>,
    isLoadingStats: Boolean,
    copyFeedback: String?,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        item {
            Text(
                text = "Share Univa AI with friends and family!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            Text(
                text = "Give your friends a great learning experience. Share your referral code below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Referral Code Card
        item {
            ReferralCodeCard(
                referralCode = referralCode,
                copyFeedback = copyFeedback,
                onCopyCode = onCopyCode
            )
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCopyCode,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Code", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onShareCode,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Stats Section
        item {
            StatsSection(
                referralLimit = referralLimit,
                referralsRemaining = referralsRemaining,
                referralsUsed = referralLimit - referralsRemaining,
                canRefer = canRefer
            )
        }

        // Referred Users Section
        if (referredUsers.isNotEmpty()) {
            item {
                Text(
                    text = "Friends You've Invited (${referredUsers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(referredUsers) { user ->
                ReferredUserCard(user = user)
            }
        } else if (!isLoadingStats) {
            item {
                EmptyReferralsView()
            }
        }

        // Loading indicator for stats
        if (isLoadingStats) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = BluePrimary
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReferralCodeCard(
    referralCode: String,
    copyFeedback: String?,
    onCopyCode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BluePrimary.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Referral Code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .border(
                        width = 2.dp,
                        color = BluePrimary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = referralCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }

            if (copyFeedback != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = copyFeedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (copyFeedback.contains("Failed")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF4CAF50)
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatsSection(
    referralLimit: Int,
    referralsRemaining: Int,
    referralsUsed: Int,
    canRefer: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Referral Stats",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Total Limit",
                    value = referralLimit.toString(),
                    icon = Icons.Default.People
                )
                StatItem(
                    label = "Used",
                    value = referralsUsed.toString(),
                    icon = Icons.Default.CheckCircle,
                    iconTint = Color(0xFF4CAF50)
                )
                StatItem(
                    label = "Remaining",
                    value = referralsRemaining.toString(),
                    icon = Icons.Default.Pending,
                    iconTint = BluePrimary
                )
            }

            if (!canRefer) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You've reached your referral limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReferredUserCard(user: ReferredUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (user.name ?: user.email).first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary
                )
            }

            // User Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = user.name ?: user.email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Joined ${formatDate(user.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Account Type Badge
            AccountTypeBadge(accountType = user.accountType)
        }
    }
}

@Composable
private fun AccountTypeBadge(accountType: String) {
    val (text, color) = when (accountType) {
        "full" -> "Full" to Color(0xFF4CAF50)
        "referral_limited" -> "Limited" to Color(0xFFFF9800)
        else -> accountType to MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyReferralsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "No referrals yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Share your code with friends to get started!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDate(isoDate: String): String {
    // Simple date formatting - can be enhanced with proper date parsing
    return try {
        val parts = isoDate.split("T")[0].split("-")
        if (parts.size == 3) {
            val year = parts[0]
            val month = parts[1].toIntOrNull()
            val day = parts[2]
            val monthName = when (month) {
                1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
                5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
                9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
                else -> month.toString()
            }
            "$monthName $day, $year"
        } else {
            isoDate
        }
    } catch (e: Exception) {
        isoDate
    }
}
