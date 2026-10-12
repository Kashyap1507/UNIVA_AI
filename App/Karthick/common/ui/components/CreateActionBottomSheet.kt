package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Create Action Bottom Sheet - Neutral Theme with Custom Layered Design
 *
 * Opened by the centre (+) button: check my work, upload course material, add a deadline.
 *
 * Design matches neutral theme:
 * - White icon circles with black icons (no colored backgrounds)
 * - Subtle shadows and elevation
 * - 18dp corner radius for consistency
 * - Clean typography hierarchy
 * - Slides up from behind bottom navigation bar
 */
@Composable
fun CreateActionBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCheckWork: () -> Unit,
    onUploadMaterial: () -> Unit,
    onAddDeadline: () -> Unit,
    modifier: Modifier = Modifier,
    bottomOffset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    CustomBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        bottomOffset = bottomOffset,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Text(
                text = "Add",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, // Theme-aware text color
                modifier = Modifier.padding(bottom = 8.dp),
            )

            CreateActionItem(
                icon = Icons.Default.CameraAlt,
                title = "Check my work",
                description = "Scan your working for hints",
                onClick = {
                    onDismiss()
                    onCheckWork()
                },
            )

            CreateActionItem(
                icon = Icons.Default.UploadFile,
                title = "Upload course material",
                description = "Syllabus, slides, PDFs or notes",
                onClick = {
                    onDismiss()
                    onUploadMaterial()
                },
            )

            CreateActionItem(
                icon = Icons.Default.Event,
                title = "Add a deadline",
                description = "Exams, assignments and quizzes",
                onClick = {
                    onDismiss()
                    onAddDeadline()
                },
            )

            Spacer(Modifier.height(24.dp)) // Extra padding above tab bar
        }
    }
}

/**
 * Individual action item in the bottom sheet
 * Theme-aware: White cards in light mode, dark gray in dark mode
 */
@Composable
private fun CreateActionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use luminance for reliable dark mode detection across platforms
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isDarkTheme) Color(0xFF2C2C2E) else Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon with circular background
            Surface(
                shape = CircleShape,
                color = if (isDarkTheme) Color(0xFF3C3C3E) else Color(0xFFF5F5F5),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.size(56.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface, // Theme-aware
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface, // Theme-aware
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }

            // Chevron indicator
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
