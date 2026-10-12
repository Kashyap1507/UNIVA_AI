package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.ui.theme.BackgroundPrimary

/**
 * GridBottomSheet Component
 *
 * 3x3 icon grid menu for action selection, matching the minimalist design from fitness app mocks.
 * Used when the "+" button is tapped in the bottom navigation.
 *
 * Design Features:
 * - Clean white background
 * - 3 columns of action items
 * - Simple monochrome icons in circles
 * - Text labels below icons
 * - Generous spacing between items
 * - Modal bottom sheet presentation
 *
 * Usage:
 * ```
 * GridBottomSheet(
 *     actions = listOf(
 *         GridAction("Scan", Icons.Default.CameraAlt, onClick = { /* scan */ }),
 *         GridAction("Upload", Icons.Default.Upload, onClick = { /* upload */ }),
 *         // ... 7 more actions
 *     ),
 *     onDismiss = { /* hide sheet */ }
 * )
 * ```
 */

/**
 * GridAction data class
 * Represents a single action item in the grid
 */
data class GridAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val iconColor: Color? = null, // Null uses default
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridBottomSheet(
    actions: List<GridAction>,
    modifier: Modifier = Modifier,
    title: String? = null,
    columns: Int = 3,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    // Use luminance for reliable dark mode detection across platforms
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else BackgroundPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            // Custom drag handle matching mocks
            Box(
                modifier =
                    Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Optional title
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }

            // Action grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(),
                content = {
                    items(actions) { action ->
                        GridActionItem(
                            action = action,
                            onDismiss = onDismiss,
                        )
                    }
                },
            )

            // Bottom padding
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * GridActionItem
 *
 * Individual action item in the grid
 */
@Composable
private fun GridActionItem(
    action: GridAction,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // No ripple for minimalist design
                    enabled = action.enabled,
                    onClick = {
                        action.onClick()
                        onDismiss()
                    },
                )
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon in circle
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (action.enabled) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                modifier = Modifier.size(28.dp),
                tint =
                    when {
                        !action.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        action.iconColor != null -> action.iconColor
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        // Label
        Text(
            text = action.label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color =
                if (action.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Simple Grid Menu (non-modal)
 *
 * For use within existing surfaces (not a bottom sheet)
 */
@Composable
fun GridActionMenu(
    actions: List<GridAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    title: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Optional title
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }

        // Action grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth(),
            content = {
                items(actions) { action ->
                    GridActionItemInline(action = action)
                }
            },
        )
    }
}

/**
 * Inline variant of GridActionItem (doesn't dismiss anything)
 */
@Composable
private fun GridActionItemInline(
    action: GridAction,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // No ripple for minimalist design
                    enabled = action.enabled,
                    onClick = action.onClick,
                )
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon in circle
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (action.enabled) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                modifier = Modifier.size(28.dp),
                tint =
                    when {
                        !action.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        action.iconColor != null -> action.iconColor
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        // Label
        Text(
            text = action.label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color =
                if (action.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
