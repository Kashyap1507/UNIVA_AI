package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Uniform section header for home screen cards
 *
 * Design matching reference:
 * - Icon (32dp) with neutral color - larger to cover title + subtitle
 * - Title text (semibold, 18sp)
 * - Optional subtitle (muted, 13sp)
 * - Optional "See all" action on right
 * - Compact padding (16dp horizontal, 8dp vertical)
 * - Entire header area is tappable when onSeeAllClick is provided
 *
 * @param title Section title (e.g., "Due this week", "Practice sets")
 * @param icon Icon to display (e.g., Icons.Default.Collections)
 * @param subtitle Optional descriptive subtitle
 * @param onSeeAllClick Optional callback for "See all" action (makes entire header tappable)
 * @param modifier Modifier for the header container
 */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onSeeAllClick != null) {
                        Modifier.clickable(onClick = onSeeAllClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left side: Icon + Title + Subtitle
        Row(
            verticalAlignment = Alignment.Top, // Align icon with title top, not center of title+subtitle
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            // Section icon - sized to match reduced header typography
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(24.dp)
                        .padding(top = 2.dp),
                // Fine-tune vertical alignment with text baseline
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            // Title + Subtitle column
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Right side: "See all" action (optional)
        if (onSeeAllClick != null) {
            Text(
                text = "See all",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
