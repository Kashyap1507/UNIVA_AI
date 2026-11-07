package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.ElevatedGlowButton
import com.kashyap.wizlyai.ui.components.ProgressiveFullImage
import com.kashyap.wizlyai.util.AccessibilityUtils
import com.kashyap.wizlyai.util.rememberHapticFeedback

/**
 * Data class representing a captured image in the scan review flow
 */
data class CapturedImageData(
    val id: String,
    val imagePath: String,
    val pageNumber: Int,
)

/**
 * Scan Review Screen - Multi-page image review with 2-column grid layout
 *
 * Used by the Check my work flow (and any other multi-page scan)
 *
 * Features:
 * - 2-column grid of captured images
 * - Add more images via camera/gallery
 * - Delete individual images
 * - Drag-to-reorder images
 * - Discard all with confirmation
 * - Configurable action button at bottom
 *
 * @param images List of captured images
 * @param actionButtonText Text for bottom action button (e.g., "Check my work")
 * @param onAddMore Callback when user taps "Add More" button
 * @param onDeleteImage Callback when user deletes an image
 * @param onReorderImages Callback when user reorders images (fromIndex, toIndex)
 * @param onActionClick Callback when user taps the bottom action button
 * @param onDiscard Callback when user confirms discarding all images
 * @param additionalControls Optional composable for additional controls below title
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    images: List<CapturedImageData>,
    actionButtonText: String,
    onAddMore: () -> Unit,
    onDeleteImage: (Int) -> Unit,
    onReorderImages: (Int, Int) -> Unit,
    onActionClick: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    additionalControls: @Composable (() -> Unit)? = null,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Drag-and-drop state
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(), // iOS safe area - top inset
        ) {
            // Top Bar
            TopAppBar(
                title = {
                    Text(
                        text = "How does this look?",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.heading(),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { showDiscardDialog = true },
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = CircleShape,
                                )
                                .semantics {
                                    contentDescription = "Discard all images and return to home"
                                },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onAddMore,
                        modifier =
                            Modifier
                                .padding(end = 8.dp)
                                .semantics {
                                    contentDescription = "Add more images from camera or gallery"
                                },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Add more",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )

            // Subtitle
            Text(
                text = "Does your solution span multiple pages? Add more images if needed.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Additional controls (optional)
            additionalControls?.invoke()

            // Empty State (when no images captured)
            if (images.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(32.dp)
                            .semantics {
                                contentDescription = "No images captured yet. Tap the button below to start scanning."
                                liveRegion = LiveRegionMode.Polite
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier =
                            Modifier
                                .size(80.dp)
                                .decorative(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No images captured yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.heading(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the button below to start scanning",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Image Grid (scrollable)
            if (images.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "${images.size} images captured. You can reorder, add more, or delete images."
                            },
                ) {
                    // Existing images
                    itemsIndexed(
                        items = images,
                        key = { _, item -> item.id },
                    ) { index, image ->
                        ImageThumbnailCard(
                            imagePath = image.imagePath,
                            index = index,
                            totalCount = images.size,
                            onDelete = { onDeleteImage(index) },
                            isDragging = draggedItemIndex == index,
                            onDragStart = { draggedItemIndex = index },
                            onDragEnd = { targetIndex ->
                                draggedItemIndex?.let { fromIndex ->
                                    if (fromIndex != targetIndex && targetIndex in images.indices) {
                                        onReorderImages(fromIndex, targetIndex)
                                    }
                                }
                                draggedItemIndex = null
                            },
                        )
                    }

                    // Add More placeholder
                    item {
                        AddMorePlaceholderCard(
                            onClick = onAddMore,
                        )
                    }
                } // End LazyVerticalGrid
            } // End if (images.isNotEmpty())

            // Bottom Action Button (fixed position)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
            ) {
                val haptic = rememberHapticFeedback()

                ElevatedGlowButton(
                    text = actionButtonText,
                    onClick = {
                        haptic.performMedium()
                        onActionClick()
                    },
                    enabled = images.isNotEmpty(),
                    glowColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Helper text when no images captured
                if (images.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Scan at least one page to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Discard Confirmation Dialog
        if (showDiscardDialog) {
            DiscardConfirmationDialog(
                onConfirm = {
                    showDiscardDialog = false
                    onDiscard()
                },
                onDismiss = {
                    showDiscardDialog = false
                },
            )
        }
    }
}

/**
 * Image thumbnail card with delete button overlay
 */
@Composable
private fun ImageThumbnailCard(
    imagePath: String,
    index: Int,
    totalCount: Int,
    onDelete: () -> Unit,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragTargeted by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    alpha = if (isDragging) 0.6f else 1f
                    scaleX = if (isDragTargeted) 0.92f else 1f
                    scaleY = if (isDragTargeted) 0.92f else 1f
                    shadowElevation = if (isDragging) 12.dp.toPx() else 2.dp.toPx()
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            onDragStart()
                        },
                        onDrag = { _, _ ->
                            // Handle drag visualization
                        },
                        onDragEnd = {
                            onDragEnd(index)
                        },
                        onDragCancel = {
                            onDragEnd(index)
                        },
                    )
                }
                .semantics {
                    contentDescription =
                        AccessibilityUtils.listItemDescription(
                            position = index + 1,
                            total = totalCount,
                            itemContent = "Scanned page image",
                        )
                    if (isDragging) {
                        stateDescription = "Currently dragging page ${index + 1}"
                    }
                },
    ) {
        // Image
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            // UX-019: Progressive loading for captured document pages
            ProgressiveFullImage(
                model = imagePath,
                contentDescription = "Captured page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Page number badge
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .decorative(),
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = "Page ${index + 1}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        // Delete button overlay
        IconButton(
            onClick = onDelete,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .shadow(4.dp, CircleShape)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                    )
                    .semantics {
                        contentDescription = "Delete page ${index + 1} of $totalCount"
                    },
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Add More placeholder card with dashed border
 */
@Composable
private fun AddMorePlaceholderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier =
            modifier
                .aspectRatio(1f)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp),
                ),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add more",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add more",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Discard confirmation dialog
 */
@Composable
private fun DiscardConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Discard media?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "If you go back now you will lose any changes you've made.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFEF4444),
                    ),
            ) {
                Text(
                    text = "Delete",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        shape = RoundedCornerShape(18.dp),
    )
}
