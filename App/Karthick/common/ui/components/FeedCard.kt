package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Feed Card component - Neutral Theme
 *
 * Updated for minimalist design matching fitness app mocks:
 * - Pure white background on light gray app background
 * - More rounded corners (18dp radius)
 * - Compact padding (16dp default for tighter layout)
 * - Subtle shadow for depth
 * - Optional three-dot menu button
 * - Clean, minimal elevation
 */
@Composable
fun FeedCard(
    modifier: Modifier = Modifier,
    showMore: Boolean = false,
    onMoreClick: () -> Unit = {},
    cornerRadius: androidx.compose.ui.unit.Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp), // Compact padding
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface, // Pure white
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = elevation,
            ),
    ) {
        Box {
            // Card content with padding
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }

            // Three-dot menu button (top-right)
            if (showMore) {
                IconButton(
                    onClick = onMoreClick,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
