package com.kashyap.wizlyai.ui.components.cards

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Usage statistics card for home dashboard
 * Shows daily usage progress for a specific feature
 *
 * Features:
 * - Circular progress indicator
 * - Usage count display (used / limit)
 * - Upgrade button when approaching limit (80%+)
 * - Color-coded: green (plenty), yellow (warning), red (limit reached)
 */
@Composable
fun UsageStatsCard(
    featureName: String,
    usedCount: Int,
    limitCount: Int,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnlimited = limitCount == -1
    val percentage = if (isUnlimited) 0f else (usedCount.toFloat() / limitCount.toFloat()).coerceIn(0f, 1f)
    val showWarning = percentage >= 0.8f && !isUnlimited
    val isLimitReached = percentage >= 1.0f && !isUnlimited

    // Color based on usage
    val progressColor = when {
        isUnlimited -> MaterialTheme.colorScheme.primary
        isLimitReached -> MaterialTheme.colorScheme.error
        showWarning -> Color(0xFFFFA726) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Circular progress and info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular progress indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = progressColor,
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    )
                    // Center text - percentage or checkmark
                    if (isUnlimited) {
                        Text(
                            text = "∞",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = progressColor
                        )
                    } else {
                        Text(
                            text = "${(percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Feature info
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = getFeatureDisplayName(featureName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isUnlimited) {
                            "Unlimited"
                        } else {
                            "$usedCount of $limitCount used"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right side - Upgrade button (if needed)
            if (showWarning || isLimitReached) {
                FilledTonalButton(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = progressColor.copy(alpha = 0.2f),
                        contentColor = progressColor
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upgrade,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Upgrade",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * Compact version of UsageStatsCard for smaller spaces
 */
@Composable
fun CompactUsageStatsCard(
    featureName: String,
    usedCount: Int,
    limitCount: Int,
    modifier: Modifier = Modifier,
) {
    val isUnlimited = limitCount == -1
    val percentage = if (isUnlimited) 0f else (usedCount.toFloat() / limitCount.toFloat()).coerceIn(0f, 1f)
    val isLimitReached = percentage >= 1.0f && !isUnlimited

    val progressColor = when {
        isUnlimited -> MaterialTheme.colorScheme.primary
        isLimitReached -> MaterialTheme.colorScheme.error
        percentage >= 0.8f -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getFeatureDisplayName(featureName),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isUnlimited) {
                    Text(
                        text = "∞",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                } else {
                    Text(
                        text = "$usedCount/$limitCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isUnlimited) {
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

/**
 * Convert API feature names to user-friendly display names
 */
private fun getFeatureDisplayName(featureName: String): String {
    return when (featureName) {
        "work_checks" -> "Work checks"
        "practice_sets" -> "Practice sets"
        "flashcard_sets" -> "AI flashcard sets"
        else -> featureName.split("_").joinToString(" ") { it.capitalize() }
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
