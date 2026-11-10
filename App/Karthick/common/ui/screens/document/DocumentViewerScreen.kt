package com.kashyap.wizlyai.ui.screens.document

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.ImageLoader
import kotlinx.coroutines.launch

/**
 * Document Viewer Screen
 * - Displays document with pages in 3D card format
 * - Allows page selection, reordering, and management
 * - Shows large photo viewer with swipe gestures
 * - Deleting a page or the whole scan
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentPages: List<String>,
    documentTitle: String = com.kashyap.wizlyai.util.DateTimeFormatter.generateDefaultDocumentTitle(),
    onBack: () -> Unit,
    onSave: (String) -> Unit = {},
    onTitleUpdate: (String) -> Unit = {},
    onPageSelect: (Int) -> Unit = {},
    onPageDelete: (Int) -> Unit = {},
    onPageRetake: (Int) -> Unit = {},
    onAddPage: () -> Unit = {},
    onNavigateToDocumentsGallery: () -> Unit = {},
    onDeleteDocument: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    animationData: AnimationData? = null,
    modifier: Modifier = Modifier,
) {
    var editingTitle by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf(documentTitle) }
    // Start with last page when returning from Add Page flow
    var selectedPageIndex by remember(documentPages.size) {
        mutableStateOf((documentPages.size - 1).coerceAtLeast(0))
    }
    var showDeleteOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDeleteDocumentConfirmation by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Animated alphas for UI fade during fullscreen
    val uiAlpha by animateFloatAsState(
        targetValue = if (isFullscreen) 0f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "uiAlpha",
    )

    val backgroundDimAlpha by animateFloatAsState(
        targetValue = if (isFullscreen) 0.95f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "backgroundDim",
    )

    // Mutable list for reordering
    var pages by remember { mutableStateOf(documentPages) }

    // Haptic feedback
    val hapticFeedback =
        if (!androidx.compose.ui.platform.LocalInspectionMode.current) {
            com.kashyap.wizlyai.util.rememberHapticFeedback()
        } else {
            null
        }

    // Animation for page transitions
    val pageTransition = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Empty state when no pages
    if (pages.isEmpty()) {
        EmptyDocumentState(
            onAddPage = onAddPage,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background scrim for fullscreen mode
        if (backgroundDimAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = backgroundDimAlpha)),
            )
        }

        // Main layout column: header, thumbnails, photo viewer, action buttons
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FA)),
        ) {
            // Header with Material 3 TopAppBar (matches app theme)
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (editingTitle) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextField(
                                    value = currentTitle,
                                    onValueChange = { currentTitle = it },
                                    textStyle =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    colors =
                                        TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                        ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                IconButton(
                                    onClick = {
                                        onTitleUpdate(currentTitle)
                                        editingTitle = false
                                    },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = "Save title changes"
                                        },
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.decorative(),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.clickable { editingTitle = true },
                            )
                            Text(
                                text = "${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (!editingTitle) {
                        // Edit button
                        IconButton(
                            onClick = { editingTitle = true },
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Edit document title"
                                },
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.decorative(),
                            )
                        }
                        // Delete document button
                        IconButton(
                            onClick = { showDeleteDocumentConfirmation = true },
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Delete entire document"
                                },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.decorative(),
                            )
                        }
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = uiAlpha },
            )

            // Thumbnail carousel with 3D cards and drag to reorder (fixed height, with fade)
            Box(modifier = Modifier.graphicsLayer { alpha = uiAlpha }) {
                ThumbnailCarousel(
                    pages = pages,
                    selectedIndex = selectedPageIndex,
                    hapticFeedback = hapticFeedback,
                    onPageSelect = { index ->
                        hapticFeedback?.performLight()
                        selectedPageIndex = index
                        onPageSelect(index)
                        coroutineScope.launch {
                            pageTransition.animateTo(
                                targetValue = index.toFloat(),
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                            )
                        }
                    },
                    onPageReorder = { fromIndex, toIndex ->
                        hapticFeedback?.performMedium()

                        // Reorder pages
                        val mutablePages = pages.toMutableList()
                        val item = mutablePages.removeAt(fromIndex)
                        mutablePages.add(toIndex, item)
                        pages = mutablePages

                        // Update selected index if needed
                        when {
                            selectedPageIndex == fromIndex -> selectedPageIndex = toIndex
                            fromIndex < selectedPageIndex && toIndex >= selectedPageIndex -> selectedPageIndex--
                            fromIndex > selectedPageIndex && toIndex <= selectedPageIndex -> selectedPageIndex++
                        }
                    },
                    onAddPage = onAddPage,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            // Simple divider matching header style (with fade)
            Divider(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = uiAlpha },
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Photo viewer with animation support - FILLS REMAINING SPACE
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { coordinates ->
                            viewSize = coordinates.size
                        },
            ) {
                // Use DocumentCardWithAnimation for any page with animationData available
                if (animationData != null) {
                    DocumentCardWithAnimation(
                        imagePath = documentPages.getOrNull(selectedPageIndex) ?: "",
                        animationData = animationData,
                        viewSize = viewSize,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Use regular PhotoViewer for other pages or when no animation
                    PhotoViewer(
                        imagePath = documentPages.getOrNull(selectedPageIndex) ?: "",
                        onSwipeLeft = {
                            if (selectedPageIndex < documentPages.size - 1) {
                                selectedPageIndex++
                                onPageSelect(selectedPageIndex)
                            }
                        },
                        onSwipeRight = {
                            if (selectedPageIndex > 0) {
                                selectedPageIndex--
                                onPageSelect(selectedPageIndex)
                            }
                        },
                        onDelete = {
                            showDeleteConfirmation = true
                        },
                        onRetake = { onPageRetake(selectedPageIndex) },
                    )
                }
            }

            // Keep the photo viewer clear of the gesture bar
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())

            // Snackbar host for error messages
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .padding(bottom = 80.dp), // Above action buttons
            )
        }
    }

    // Delete page confirmation dialog
    if (showDeleteConfirmation) {
        ThemedAlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Page?") },
            text = { Text("Are you sure you want to delete page ${selectedPageIndex + 1}? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        if (pages.size > 1) {
                            hapticFeedback?.performMedium()

                            // Remove page from list
                            val mutablePages = pages.toMutableList()
                            mutablePages.removeAt(selectedPageIndex)
                            pages = mutablePages

                            // Update selected index
                            if (selectedPageIndex >= pages.size && selectedPageIndex > 0) {
                                selectedPageIndex--
                            }

                            // Notify parent
                            onPageDelete(selectedPageIndex)
                        }
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFEF4444),
                        ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete entire document confirmation dialog
    if (showDeleteDocumentConfirmation) {
        ThemedAlertDialog(
            onDismissRequest = { showDeleteDocumentConfirmation = false },
            title = { Text("Delete Document?") },
            text = {
                Text(
                    "Delete this scan and all its pages? This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDocumentConfirmation = false
                        hapticFeedback?.performHeavy()
                        onDeleteDocument()
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFEF4444),
                        ),
                ) {
                    Text("Delete Document")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDocumentConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Thumbnail carousel with 3D card effect and polished drag to reorder
 */
@Composable
private fun ThumbnailCarousel(
    pages: List<String>,
    selectedIndex: Int,
    hapticFeedback: com.kashyap.wizlyai.util.HapticFeedback?,
    onPageSelect: (Int) -> Unit,
    onPageReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetDropIndex by remember { mutableStateOf<Int?>(null) }
    var isDragActive by remember { mutableStateOf(false) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(pages, key = { _, path -> path }) { index, imagePath ->
            Box {
                // Drop zone indicator (shown before each card when dragging)
                if (isDragActive && draggedIndex != index && targetDropIndex == index) {
                    DropZoneIndicator(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-12).dp),
                    )
                }

                ThumbnailCard(
                    imagePath = imagePath,
                    pageNumber = index + 1,
                    totalPages = pages.size,
                    isSelected = index == selectedIndex,
                    isDragging = draggedIndex == index,
                    isDropTarget = isDragActive && targetDropIndex == index && draggedIndex != index,
                    onClick = {
                        if (!isDragActive) {
                            onPageSelect(index)
                        }
                    },
                    onLongPress = {
                        // Enable drag mode on long press with haptic feedback
                        hapticFeedback?.performHeavy()
                        draggedIndex = index
                        isDragActive = true
                        targetDropIndex = index
                    },
                    onDragEnd = {
                        draggedIndex?.let { from ->
                            targetDropIndex?.let { to ->
                                if (from != to) {
                                    onPageReorder(from, to)
                                }
                            }
                        }
                        draggedIndex = null
                        targetDropIndex = null
                        isDragActive = false
                    },
                    onDragOver = { hoveredIndex ->
                        targetDropIndex = hoveredIndex
                    },
                    isDragEnabled = draggedIndex == index,
                )
            }
        }

        // Drop zone at the end (after last card)
        if (isDragActive && draggedIndex != pages.size) {
            item {
                DropZoneIndicator(
                    isLast = true,
                    modifier = Modifier.offset(x = (-12).dp),
                )
            }
        }

        // Add page card
        item {
            AddPageCard(onClick = onAddPage)
        }
    }
}

/**
 * Drop zone indicator - shows where card will be dropped
 */
@Composable
private fun DropZoneIndicator(
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "dropZonePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dropZoneAlpha",
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dropZoneScale",
    )

    Box(
        modifier =
            modifier
                .width(4.dp)
                .height(110.dp)
                .graphicsLayer {
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color(0xFF6366F1).copy(alpha = 0.3f),
                                Color(0xFF6366F1),
                                Color(0xFF6366F1).copy(alpha = 0.3f),
                            ),
                    ),
                    shape = RoundedCornerShape(2.dp),
                )
                .shadow(4.dp, RoundedCornerShape(2.dp)),
    )
}

/**
 * 3D Thumbnail card with selection state, page number, and polished drag support
 */
@Composable
private fun ThumbnailCard(
    imagePath: String,
    pageNumber: Int,
    totalPages: Int,
    isSelected: Boolean,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    isDragEnabled: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragOver: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue =
            when {
                isDragging -> 1.25f
                isDropTarget -> 1.05f
                isSelected -> 1.15f
                else -> 1f
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbnailScale",
    )

    val elevation by animateDpAsState(
        targetValue =
            when {
                isDragging -> 20.dp
                isDropTarget -> 8.dp
                isSelected -> 12.dp
                else -> 4.dp
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbnailElevation",
    )

    val rotationY by animateFloatAsState(
        targetValue =
            when {
                isDragging -> 0f
                isSelected -> 0f
                else -> 8f
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbnailRotation",
    )

    val rotationZ by animateFloatAsState(
        targetValue = if (isDragging) 5f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbnailRotationZ",
    )

    var dragOffsetX by remember { mutableStateOf(0f) }
    val cardWidth = 90.dp
    val density = LocalDensity.current
    val cardWidthPx = with(density) { (cardWidth.toPx() + 16.dp.toPx()) } // card + spacing

    Card(
        modifier =
            modifier
                .size(width = 90.dp, height = 110.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.rotationY = rotationY
                    this.rotationZ = rotationZ
                    shadowElevation = elevation.toPx()
                    ambientShadowColor = Color.Black.copy(alpha = 0.3f)
                    spotShadowColor = Color.Black.copy(alpha = 0.3f)
                    // Only horizontal translation (constrained to thumbnail row)
                    translationX = if (isDragEnabled) dragOffsetX else 0f
                    translationY = 0f // No vertical movement
                    alpha = if (isDragging && !isDragEnabled) 0.3f else 1f
                }
                .shadow(
                    elevation = elevation,
                    shape = RoundedCornerShape(18.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                    spotColor = Color.Black.copy(alpha = 0.3f),
                )
                .pointerInput(isDragEnabled) {
                    if (isDragEnabled) {
                        detectDragGestures(
                            onDragEnd = {
                                dragOffsetX = 0f
                                onDragEnd()
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                                onDragEnd()
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                // Only allow horizontal dragging
                                dragOffsetX += delta.x

                                // Calculate which card we're hovering over
                                val cardsMoved = (dragOffsetX / cardWidthPx).toInt()
                                val newIndex = (pageNumber - 1 + cardsMoved).coerceIn(0, totalPages - 1)
                                onDragOver(newIndex)
                            },
                        )
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        onLongPress()
                        // TODO: Add haptic feedback here
                    },
                ),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border =
            if (isSelected) {
                androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF6366F1))
            } else {
                null
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ImageLoader(
                imagePath = imagePath,
                contentDescription = "Page $pageNumber of $totalPages",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
                showLoading = false,
            )

            // Page number badge
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "$pageNumber",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Drag hint overlay (shown when dragging)
            if (isDragging) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Dragging",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(32.dp)
                                .shadow(4.dp, CircleShape)
                                .background(Color(0xFF6366F1).copy(alpha = 0.8f), CircleShape)
                                .padding(6.dp),
                    )
                }
            }

            // Long-press hint (shown when not dragging and not selected)
            if (!isDragging && !isSelected && !isDragEnabled) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Long press to reorder",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(16.dp),
                )
            }
        }
    }
}

/**
 * Add page card with dashed border
 */
@Composable
private fun AddPageCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(width = 90.dp, height = 110.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(18.dp),
                    clip = false,
                )
                .clickable(onClick = onClick)
                .background(Color(0xFFFAFAFA), RoundedCornerShape(18.dp))
                .dashedBorder(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    cornerRadiusDp = 16.dp,
                    dashWidth = 12.dp,
                    gapWidth = 8.dp,
                )
                .semantics {
                    contentDescription = "Add new page to document"
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp).decorative(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Add",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Extension function to add dashed border
 */
private fun Modifier.dashedBorder(
    strokeWidth: androidx.compose.ui.unit.Dp,
    color: Color,
    cornerRadiusDp: androidx.compose.ui.unit.Dp,
    dashWidth: androidx.compose.ui.unit.Dp = 10.dp,
    gapWidth: androidx.compose.ui.unit.Dp = 5.dp,
) = composed(
    factory = {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val strokeWidthPx = density.run { strokeWidth.toPx() }
        val cornerRadiusPx = density.run { cornerRadiusDp.toPx() }
        val dashWidthPx = density.run { dashWidth.toPx() }
        val gapWidthPx = density.run { gapWidth.toPx() }

        Modifier.drawWithContent {
            drawContent()
            val pathEffect =
                androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(dashWidthPx, gapWidthPx),
                    0f,
                )
            drawRoundRect(
                color = color,
                style =
                    androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthPx,
                        pathEffect = pathEffect,
                    ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
            )
        }
    },
)

/**
 * Large photo viewer with swipe gestures and action overlays
 */
@Composable
private fun PhotoViewer(
    imagePath: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDelete: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }

    Card(
        modifier =
            modifier
                .fillMaxSize()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    clip = false,
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 100) {
                                onSwipeRight()
                            } else if (dragOffset < -100) {
                                onSwipeLeft()
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            dragOffset += delta
                        },
                    )
                },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ImageLoader(
                imagePath = imagePath,
                contentDescription = "Document page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                showLoading = true,
            )

            // Horizontal 3-dot menu with translucent background
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
            ) {
                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.3f),
                                        ),
                                ),
                                RoundedCornerShape(8.dp),
                            )
                            .semantics {
                                contentDescription = "More page options"
                            },
                ) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.decorative(),
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Retake", color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onRetake()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Delete", color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Empty state when document has no pages
 */
@Composable
private fun EmptyDocumentState(
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .semantics {
                    contentDescription = "No pages in document. Add your first page to get started."
                    liveRegion = LiveRegionMode.Polite
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp).decorative(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "No Pages Yet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heading(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Add your first page to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onAddPage,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Add first page to document"
                    },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                    ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).decorative(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add Page",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * DEPRECATED: Use DateTimeFormatter.generateDefaultDocumentTitle() instead
 * This function has been replaced by a centralized utility to ensure
 * consistent document titles across the app.
 *
 * Old format: "new doc 2025-10-07 23.42.17"
 * New format: "New Doc - 2025-10-07 23:42:17"
 */
@Deprecated(
    message = "Use DateTimeFormatter.generateDefaultDocumentTitle() instead",
    replaceWith =
        ReplaceWith(
            "com.kashyap.wizlyai.util.DateTimeFormatter.generateDefaultDocumentTitle()",
            "com.kashyap.wizlyai.util.DateTimeFormatter",
        ),
    level = DeprecationLevel.ERROR,
)
private fun generateDefaultTitle(): String {
    return com.kashyap.wizlyai.util.DateTimeFormatter.generateDefaultDocumentTitle()
}
