package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kashyap.wizlyai.data.preferences.PreferenceKeys
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.ui.components.MagnifyingLoupe
import com.kashyap.wizlyai.ui.components.ThemedAlertDialog
import com.kashyap.wizlyai.util.rememberHapticFeedback
import com.kashyap.wizlyai.vision.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.sqrt

private enum class CornerHandle { TopLeft, TopRight, BottomRight, BottomLeft }

private enum class SideHandle { Top, Right, Bottom, Left }

private sealed interface CropHandle {
    data class Corner(val id: CornerHandle) : CropHandle

    data class Side(val id: SideHandle) : CropHandle
}

private val CornerOrder =
    listOf(
        CornerHandle.TopLeft,
        CornerHandle.TopRight,
        CornerHandle.BottomRight,
        CornerHandle.BottomLeft,
    )

private val SideOrder =
    listOf(
        SideHandle.Top to (0 to 1),
        SideHandle.Right to (1 to 2),
        SideHandle.Bottom to (2 to 3),
        SideHandle.Left to (3 to 0),
    )

private const val LOUPE_SCALE = 2.5f

/**
 * Photo Review Screen with OpenCV Edge Detection
 *
 * Features:
 * - Automatic edge detection using OpenCV (Android) or manual mode (iOS)
 * - 70% confidence threshold for auto vs manual mode
 * - Draggable corner handles for manual adjustment
 * - Rotation controls (rotate left/right) - visible after edge detection
 * - Debug info overlay (conditional on developer setting)
 * - Perspective transform with high-quality output
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReviewScreenWithEdgeDetection(
    imagePath: String,
    onRetake: () -> Unit,
    onUsePhoto: (String, DocumentEdges?, Int) -> Unit,
    preferencesManager: PreferencesManager,
    imageEnhancer: com.kashyap.wizlyai.util.ImageEnhancer? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val detector = remember { EdgeDetector() }
    val haptic = rememberHapticFeedback()

    // State
    var detectionMode by remember { mutableStateOf(DetectionMode.AUTO) }
    var edges by remember { mutableStateOf<DocumentEdges?>(null) }
    var rotation by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(true) }
    var isDetectionComplete by remember { mutableStateOf(false) }
    var isCropModeActive by remember { mutableStateOf(false) } // NEW: Track crop mode activation
    var detectionSucceeded by remember { mutableStateOf(false) } // NEW: Track if detection succeeded
    var imageSize by remember { mutableStateOf<ImageSize?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf("Initializing...") }
    var detectionDuration by remember { mutableStateOf(0L) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) } // Track if haptic already triggered

    // Processing state
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var processingErrorMessage by remember { mutableStateOf("") }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    // Read developer setting for debug overlay
    val showDebugOverlay =
        remember {
            preferencesManager.getBoolean(PreferenceKeys.SHOW_DEBUG_INFO_OVERLAY, false)
        }

    // Auto-detect edges on initial load
    LaunchedEffect(imagePath) {
        try {
            val detectionStartTime = Clock.System.now().toEpochMilliseconds()
            debugInfo = "Getting image size..."

            imageSize = detector.getImageSize(imagePath)
            debugInfo = "Image: ${imageSize?.width}x${imageSize?.height}\nDetecting edges..."

            val detected = detector.detectEdges(imagePath)
            detectionDuration = Clock.System.now().toEpochMilliseconds() - detectionStartTime

            if (detected != null) {
                // Use detected edges regardless of confidence (HIGH, MEDIUM, or LOW)
                // User can manually adjust if needed
                edges = detected
                detectionMode = detected.mode
                detectionSucceeded = true // Mark detection as successful
                isCropModeActive = true // Auto-show crop bounds when detection succeeds

                // Trigger haptic feedback on HIGH confidence detection (only once per image)
                if (detected.confidence == DetectionConfidence.HIGH && !hasTriggeredHaptic) {
                    haptic.performSuccess()
                    hasTriggeredHaptic = true
                }

                debugInfo =
                    buildString {
                        append("✓ Detected (${detectionDuration}ms)\n")
                        append("Mode: ${detected.mode.name}\n")
                        append("Confidence: ${detected.confidence.name}")
                        if (detected.confidence == DetectionConfidence.HIGH) {
                            append(" (>70%)")
                        } else if (detected.confidence == DetectionConfidence.MEDIUM) {
                            append(" (40-70%)")
                        } else if (detected.confidence == DetectionConfidence.LOW) {
                            append(" (<40%)")
                        }
                    }
            } else {
                // No detection: Prepare default corners but don't show them yet
                // User must tap crop icon to activate crop mode
                imageSize?.let {
                    val defaultEdges = detector.getDefaultCorners(it.width, it.height)
                    edges = insetEdges(defaultEdges, it, 0.1f)
                    detectionMode = DetectionMode.MANUAL
                    detectionSucceeded = false // Mark detection as failed
                    isCropModeActive = false // Don't auto-show crop bounds
                    debugInfo = "No document detected\nTap crop icon to adjust (${detectionDuration}ms)"
                }
            }
        } catch (e: Exception) {
            // Fallback to manual mode on error
            errorMessage = "Detection failed: ${e.message}"
            debugInfo = "ERROR: ${e.message}"
            imageSize?.let {
                val defaultEdges = detector.getDefaultCorners(it.width, it.height)
                edges = insetEdges(defaultEdges, it, 0.1f)
                detectionMode = DetectionMode.MANUAL
            }
        } finally {
            isProcessing = false
            isDetectionComplete = true
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        // Background image - fit to screen with letterboxing/pillarboxing and rotation
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black) // Black background for letterbox/pillarbox areas
                    .onGloballyPositioned { coordinates ->
                        viewSize = coordinates.size
                    },
            contentAlignment = Alignment.Center,
        ) {
            PreviewImage(
                imagePath = imagePath,
                rotation = rotation,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Edge overlay with draggable corners (only show when crop mode is active)
        if (edges != null && imageSize != null && viewSize != IntSize.Zero && !isProcessing && isDetectionComplete && isCropModeActive) {
            Box(
                modifier =
                    Modifier.fillMaxSize().semantics {
                        contentDescription = "Crop boundary overlay. Drag corners to adjust document boundaries"
                        stateDescription = "Crop mode active. ${CornerOrder.size} adjustable corner handles"
                    },
            ) {
                EdgeOverlay(
                    edges = edges!!,
                    imagePath = imagePath,
                    imageSize = imageSize!!,
                    viewSize = viewSize,
                    rotation = rotation, // Pass rotation to edge overlay
                    showCropHandles = true, // Always allow manual adjustment
                    onEdgesChanged = { newEdges ->
                        edges =
                            newEdges.copy(
                                mode = DetectionMode.MANUAL,
                                confidence = DetectionConfidence.LOW,
                            )
                        detectionMode = DetectionMode.MANUAL
                    },
                )
            }
        }

        // Top header with back button and crop/rotation controls
        AnimatedVisibility(
            visible = isDetectionComplete,
            enter =
                fadeIn(animationSpec = tween(300)) +
                    slideInVertically(
                        animationSpec = tween(300),
                        initialOffsetY = { -it },
                    ),
            exit =
                fadeOut(animationSpec = tween(200)) +
                    slideOutVertically(
                        animationSpec = tween(200),
                        targetOffsetY = { -it },
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button
                IconButton(
                    onClick = { showDiscardConfirmation = true },
                    modifier =
                        Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape,
                            )
                            .semantics { contentDescription = "Go back and retake photo" },
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Right side controls (always visible: crop toggle + rotation controls)
                if (isDetectionComplete) {
                    Row(
                        modifier =
                            Modifier
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Crop toggle icon (changes based on crop mode state)
                        IconButton(
                            onClick = {
                                isCropModeActive = !isCropModeActive
                            },
                            modifier =
                                Modifier.semantics {
                                    contentDescription =
                                        if (isCropModeActive) {
                                            "Close crop mode and use full image"
                                        } else {
                                            "Enter crop mode to adjust document boundaries"
                                        }
                                    stateDescription = if (isCropModeActive) "Crop mode active" else "Crop mode inactive"
                                },
                        ) {
                            Icon(
                                imageVector = if (isCropModeActive) Icons.Default.Close else Icons.Default.CropFree,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        // Rotate Left
                        IconButton(
                            onClick = {
                                rotation = (rotation + 270) % 360
                            },
                            enabled = !isProcessing,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Rotate image 90 degrees counter-clockwise. Current rotation: $rotation degrees"
                                    if (isProcessing) disabled()
                                },
                        ) {
                            Icon(
                                Icons.Default.RotateLeft,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        // Rotate Right
                        IconButton(
                            onClick = {
                                rotation = (rotation + 90) % 360
                            },
                            enabled = !isProcessing,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Rotate image 90 degrees clockwise. Current rotation: $rotation degrees"
                                    if (isProcessing) disabled()
                                },
                        ) {
                            Icon(
                                Icons.Default.RotateRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }

        // Unified Debug info overlay (only if developer setting enabled)
        if (showDebugOverlay) {
            Card(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 80.dp, start = 16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (errorMessage != null) {
                                Color.Red.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                    ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "DEBUG INFO",
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))

                    // Detection mode and confidence
                    edges?.let { e ->
                        Text(
                            text = "Mode: ${e.mode.name} | ${e.confidence.name}",
                            color =
                                if (e.confidence == DetectionConfidence.HIGH) {
                                    Color.Green
                                } else if (e.confidence == DetectionConfidence.MEDIUM) {
                                    Color.Yellow
                                } else {
                                    Color(0xFFFF6B6B)
                                },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Image & view size
                    imageSize?.let { imgSize ->
                        Text(
                            text = "Img: ${imgSize.width}x${imgSize.height}",
                            color = Color.White,
                            fontSize = 9.sp,
                        )
                    }
                    if (viewSize != IntSize.Zero) {
                        Text(
                            text = "View: ${viewSize.width}x${viewSize.height}",
                            color = Color.Gray,
                            fontSize = 9.sp,
                        )
                    }

                    // Rotation
                    if (rotation != 0) {
                        Text(
                            text = "Rotation: $rotation°",
                            color = Color.Cyan,
                            fontSize = 9.sp,
                        )
                    }

                    // Detection duration
                    if (detectionDuration > 0) {
                        Text(
                            text = "Detection: ${detectionDuration}ms",
                            color = Color.Gray,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }

        // Bottom action buttons - matching reference design
        AnimatedVisibility(
            visible = isDetectionComplete,
            enter =
                fadeIn(animationSpec = tween(300)) +
                    slideInVertically(
                        animationSpec = tween(300),
                        initialOffsetY = { it },
                    ),
            exit =
                fadeOut(animationSpec = tween(200)) +
                    slideOutVertically(
                        animationSpec = tween(200),
                        targetOffsetY = { it },
                    ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 48.dp, end = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Retake button (red circle with refresh icon)
                IconButton(
                    onClick = { showDiscardConfirmation = true },
                    enabled = !isProcessing,
                    modifier =
                        Modifier
                            .size(72.dp)
                            .background(
                                Color(0xFFDC2626).copy(alpha = if (isProcessing) 0.5f else 1f),
                                CircleShape,
                            )
                            .semantics {
                                contentDescription =
                                    if (isProcessing) {
                                        "Processing image, please wait"
                                    } else {
                                        "Retake photo to capture a new image"
                                    }
                            },
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Retake",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Accept button (green circle with checkmark)
                IconButton(
                    onClick = {
                        // --- FIX: Use a final check on the *current* edges ---
                        val finalEdges = if (isCropModeActive) edges else null

                        scope.launch(Dispatchers.Main) {
                            try {
                                val croppedPath =
                                    detector.applyPerspectiveTransform(
                                        imagePath,
                                        finalEdges, // Pass null if crop mode is off
                                        rotation,
                                    )

                                val enhanced =
                                    if (imageEnhancer != null) {
                                        imageEnhancer.enhanceDocument(
                                            croppedPath,
                                            com.kashyap.wizlyai.util.EnhancementMode.BALANCED,
                                        )
                                    } else {
                                        croppedPath
                                    }

                                // Directly navigate to DocumentViewerScreen with animation data
                                onUsePhoto(enhanced, finalEdges, rotation)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                processingErrorMessage = e.message ?: "Unknown error occurred during processing"
                                showErrorDialog = true
                                isProcessingPhoto = false
                            }
                        }
                    },
                    enabled = !isProcessingPhoto, // Always enabled even if edges are null
                    modifier =
                        Modifier
                            .size(72.dp)
                            .background(
                                Color(0xFF16A34A).copy(alpha = if (isProcessingPhoto) 0.5f else 1f),
                                CircleShape,
                            )
                            .semantics {
                                contentDescription =
                                    when {
                                        isProcessingPhoto -> "Processing photo, please wait"
                                        !isCropModeActive -> "Accept and continue with full image"
                                        else -> "Accept and continue with cropped image"
                                    }
                            },
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Accept",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Button labels
        AnimatedVisibility(
            visible = isDetectionComplete,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp, start = 48.dp, end = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = "Retake",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Accept",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Loading indicator (for cropping/enhancing)
        if (isProcessingPhoto) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Color.Cyan)
                    Text(
                        text = "Processing photo...",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        // POLISH-020: Discard confirmation dialog
        if (showDiscardConfirmation) {
            ThemedAlertDialog(
                onDismissRequest = { showDiscardConfirmation = false },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                },
                title = {
                    Text(
                        text = "Discard Photo?",
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(text = "Going back will discard this photo and you'll need to take a new one. Are you sure?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardConfirmation = false
                            onRetake()
                        },
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmation = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // POLISH-015: Error dialog with retry functionality
        if (showErrorDialog) {
            ThemedAlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = {
                    Text(
                        text = "Processing Failed",
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(text = processingErrorMessage)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showErrorDialog = false
                            // Retry processing
                            val finalEdges = if (isCropModeActive) edges else null
                            isProcessingPhoto = true
                            scope.launch(Dispatchers.Default) {
                                try {
                                    val croppedPath =
                                        detector.applyPerspectiveTransform(
                                            imagePath,
                                            finalEdges,
                                            rotation,
                                        )
                                    val enhanced =
                                        if (imageEnhancer != null) {
                                            imageEnhancer.enhanceDocument(
                                                croppedPath,
                                                com.kashyap.wizlyai.util.EnhancementMode.BALANCED,
                                            )
                                        } else {
                                            croppedPath
                                        }
                                    withContext(Dispatchers.Main) {
                                        onUsePhoto(enhanced, finalEdges, rotation)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        processingErrorMessage = e.message ?: "Unknown error"
                                        showErrorDialog = true
                                        isProcessingPhoto = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text("Retry", color = Color.Cyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun PreviewImage(
    imagePath: String,
    rotation: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val imageRequest =
        remember(imagePath, context) {
            val data = if (imagePath.startsWith("file://")) imagePath else "file://$imagePath"
            ImageRequest.Builder(context)
                .data(data)
                .crossfade(true)
                .build()
        }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.rotate(rotation.toFloat()),
    )
}

@Composable
private fun EdgeOverlay(
    edges: DocumentEdges,
    imagePath: String,
    imageSize: ImageSize, // This is the *original* image size
    viewSize: IntSize,
    rotation: Int,
    showCropHandles: Boolean,
    onEdgesChanged: (DocumentEdges) -> Unit,
) {
    // Use parent edges parameter directly - no local state needed
    // This ensures corner positions update from parent between drags
    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var loupePosition by remember { mutableStateOf<Offset?>(null) }

    // Track if user is actively dragging
    val isDragging = activeHandle != null

    LaunchedEffect(rotation) {
        zoom = 1f
        panOffset = Offset.Zero
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glowAnimation")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "glowAlpha",
    )

    val showGlowAnimation =
        edges.confidence == DetectionConfidence.HIGH &&
            edges.mode == DetectionMode.AUTO

    val rotatedImageSize =
        remember(imageSize, rotation) {
            if (rotation == 90 || rotation == 270) {
                ImageSize(width = imageSize.height, height = imageSize.width)
            } else {
                imageSize
            }
        }

    val baseScale =
        remember(rotatedImageSize, viewSize) {
            val scaleX = viewSize.width.toFloat() / rotatedImageSize.width
            val scaleY = viewSize.height.toFloat() / rotatedImageSize.height
            minOf(scaleX, scaleY)
        }
    val scale = baseScale * zoom

    val baseOffsetX =
        remember(rotatedImageSize, viewSize, baseScale) {
            (viewSize.width - rotatedImageSize.width * baseScale) / 2f
        }
    val baseOffsetY =
        remember(rotatedImageSize, viewSize, baseScale) {
            (viewSize.height - rotatedImageSize.height * baseScale) / 2f
        }
    val offsetX = baseOffsetX + panOffset.x
    val offsetY = baseOffsetY + panOffset.y

    fun rotatePoint(
        point: Point,
        rotation: Int,
        imgSize: ImageSize,
    ): Point {
        return when (rotation) {
            90 -> Point(imgSize.height - point.y, point.x)
            180 -> Point(imgSize.width - point.x, imgSize.height - point.y)
            270 -> Point(imgSize.width - point.y, point.x)
            else -> point
        }
    }

    fun inverseRotatePoint(
        point: Point,
        rotation: Int,
        imgSize: ImageSize,
    ): Point {
        return when (rotation) {
            90 -> Point(point.y, imgSize.height - point.x)
            180 -> Point(imgSize.width - point.x, imgSize.height - point.y)
            270 -> Point(imgSize.width - point.y, point.x)
            else -> point
        }
    }

    val rotatedCorners =
        remember(edges, rotation, imageSize) {
            listOf(
                rotatePoint(edges.topLeft, rotation, imageSize),
                rotatePoint(edges.topRight, rotation, imageSize),
                rotatePoint(edges.bottomRight, rotation, imageSize),
                rotatePoint(edges.bottomLeft, rotation, imageSize),
            )
        }

    val screenCorners =
        remember(rotatedCorners, scale, offsetX, offsetY) {
            rotatedCorners.map { corner ->
                Offset(
                    x = corner.x * scale + offsetX,
                    y = corner.y * scale + offsetY,
                )
            }
        }

    val onHandleDragStart: (CropHandle, Offset) -> Unit = { handle, position ->
        activeHandle = handle

        // Transform screen coordinates to image coordinates for loupe
        val imageX = (position.x - offsetX) / scale
        val imageY = (position.y - offsetY) / scale
        loupePosition = Offset(imageX, imageY)
    }
    val onHandleDragUpdate: (Offset) -> Unit = { position ->
        // Transform screen coordinates to image coordinates for loupe
        val imageX = (position.x - offsetX) / scale
        val imageY = (position.y - offsetY) / scale
        loupePosition = Offset(imageX, imageY)
    }
    val onHandleDragEnd: () -> Unit = {
        activeHandle = null
        loupePosition = null
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // -------------------------------------------------
                // --- FIX: Added gesture detector to drag the whole grid ---
                // -------------------------------------------------
                .pointerInput(rotation, imageSize, scale, activeHandle) {
                    detectDragGestures(
                        onDragStart = {
                            // This drag will only start if no handles are being dragged
                            // (handles are in separate Boxes and will consume the gesture)
                        },
                        onDrag = { change, dragAmount ->
                            // Only drag if no specific handle is active
                            if (activeHandle == null) {
                                change.consume()

                                // 1. Convert screen dragAmount to *rotated image* space delta
                                val deltaRotatedX = dragAmount.x / scale
                                val deltaRotatedY = dragAmount.y / scale

                                // 2. Get current *rotated* corners
                                val rotatedTL = rotatePoint(edges.topLeft, rotation, imageSize)
                                val rotatedTR = rotatePoint(edges.topRight, rotation, imageSize)
                                val rotatedBR = rotatePoint(edges.bottomRight, rotation, imageSize)
                                val rotatedBL = rotatePoint(edges.bottomLeft, rotation, imageSize)
                                val rotatedPoints = listOf(rotatedTL, rotatedTR, rotatedBR, rotatedBL)

                                // 3. Calculate new *rotated* points
                                val newRotatedPoints =
                                    rotatedPoints.map {
                                        Point(it.x + deltaRotatedX, it.y + deltaRotatedY)
                                    }

                                // 4. Apply delta directly without boundary checks
                                val finalNewRotatedPoints =
                                    rotatedPoints.map {
                                        Point(it.x + deltaRotatedX, it.y + deltaRotatedY)
                                    }

                                // 6. Inversely rotate the new points to get *original* image space points
                                val newOriginalTL = inverseRotatePoint(finalNewRotatedPoints[0], rotation, imageSize)
                                val newOriginalTR = inverseRotatePoint(finalNewRotatedPoints[1], rotation, imageSize)
                                val newOriginalBR = inverseRotatePoint(finalNewRotatedPoints[2], rotation, imageSize)
                                val newOriginalBL = inverseRotatePoint(finalNewRotatedPoints[3], rotation, imageSize)

                                // 7. Create new DocumentEdges and call onEdgesChanged
                                val newEdges =
                                    edges.copy(
                                        topLeft = newOriginalTL,
                                        topRight = newOriginalTR,
                                        bottomRight = newOriginalBR,
                                        bottomLeft = newOriginalBL,
                                        mode = DetectionMode.MANUAL, // Dragging makes it manual
                                        confidence = DetectionConfidence.LOW,
                                    )
                                onEdgesChanged(newEdges)
                            }
                        },
                        onDragEnd = { },
                    )
                }
                .pointerInput(zoom) {
                    detectTransformGestures(panZoomLock = false) { _, pan, zoomChange, _ ->
                        val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        zoom = newZoom
                        if (zoom > 1f) {
                            panOffset += pan
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCropShape(
                screenCorners = screenCorners,
                showCropHandles = showCropHandles,
                showGlowAnimation = showGlowAnimation,
                glowAlpha = glowAlpha,
                activeCorner = (activeHandle as? CropHandle.Corner)?.id,
                activeSide = (activeHandle as? CropHandle.Side)?.id,
            )
        }

        if (showCropHandles) {
            DraggableCorners(
                edges = edges,
                imageSize = imageSize,
                rotation = rotation,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                rotatePointFn = ::rotatePoint,
                inverseRotatePointFn = ::inverseRotatePoint,
                onEdgesChanged = onEdgesChanged,
                onHandleDragStart = onHandleDragStart,
                onHandleDragUpdate = onHandleDragUpdate,
                onHandleDragEnd = onHandleDragEnd,
            )

            DraggableSideHandles(
                edges = edges,
                imageSize = imageSize,
                rotation = rotation,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                rotatePointFn = ::rotatePoint,
                inverseRotatePointFn = ::inverseRotatePoint,
                onEdgesChanged = onEdgesChanged,
                onHandleDragStart = onHandleDragStart,
                onHandleDragUpdate = onHandleDragUpdate,
                onHandleDragEnd = onHandleDragEnd,
            )
        }

        val loupeCenter = loupePosition
        if (loupeCenter != null) {
            MagnifyingLoupe(
                touchPosition = loupeCenter, // Now in image coordinates
                viewSize =
                    IntSize(
                        width = rotatedImageSize.width, // Image size, not screen size
                        height = rotatedImageSize.height,
                    ),
                loupeScale = LOUPE_SCALE,
                modifier = Modifier.zIndex(3f),
            ) {
                PreviewImage(
                    imagePath = imagePath,
                    rotation = rotation,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCropShape(
                        screenCorners = screenCorners,
                        showCropHandles = showCropHandles,
                        showGlowAnimation = showGlowAnimation,
                        glowAlpha = glowAlpha,
                        activeCorner = (activeHandle as? CropHandle.Corner)?.id,
                        activeSide = (activeHandle as? CropHandle.Side)?.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableCorners(
    edges: DocumentEdges,
    imageSize: ImageSize,
    rotation: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotatePointFn: (Point, Int, ImageSize) -> Point,
    inverseRotatePointFn: (Point, Int, ImageSize) -> Point,
    onEdgesChanged: (DocumentEdges) -> Unit,
    onHandleDragStart: (CropHandle, Offset) -> Unit,
    onHandleDragUpdate: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    // Use rememberUpdatedState to ensure lambda always reads latest edges value
    // This prevents stale closure captures when edges changes
    val currentEdges by rememberUpdatedState(edges)

    val rotatedImageSize =
        if (rotation == 90 || rotation == 270) {
            ImageSize(imageSize.height, imageSize.width)
        } else {
            imageSize
        }

    CornerOrder.forEach { cornerId ->
        val originalPoint =
            when (cornerId) {
                CornerHandle.TopLeft -> currentEdges.topLeft
                CornerHandle.TopRight -> currentEdges.topRight
                CornerHandle.BottomRight -> currentEdges.bottomRight
                CornerHandle.BottomLeft -> currentEdges.bottomLeft
            }

        val rotatedCorner = rotatePointFn(originalPoint, rotation, imageSize)

        DraggableCorner(
            handle = cornerId,
            corner = rotatedCorner,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            imageSize = rotatedImageSize,
            onDrag = { newRotatedPoint ->
                val newOriginalPoint = inverseRotatePointFn(newRotatedPoint, rotation, imageSize)
                val updatedEdges =
                    when (cornerId) {
                        CornerHandle.TopLeft -> currentEdges.copy(topLeft = newOriginalPoint)
                        CornerHandle.TopRight -> currentEdges.copy(topRight = newOriginalPoint)
                        CornerHandle.BottomRight -> currentEdges.copy(bottomRight = newOriginalPoint)
                        CornerHandle.BottomLeft -> currentEdges.copy(bottomLeft = newOriginalPoint)
                    }
                onEdgesChanged(updatedEdges)
            },
            onHandleDragStart = onHandleDragStart,
            onHandleDragUpdate = onHandleDragUpdate,
            onHandleDragEnd = onHandleDragEnd,
        )
    }
}

@Composable
private fun DraggableCorner(
    handle: CornerHandle,
    corner: Point,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    imageSize: ImageSize,
    onDrag: (Point) -> Unit,
    onHandleDragStart: (CropHandle, Offset) -> Unit,
    onHandleDragUpdate: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    val density = LocalDensity.current

    // Track drag state
    var isDragging by remember { mutableStateOf(false) }

    // Track position - updates from corner only when NOT dragging
    var dragStartImagePos by remember { mutableStateOf(corner) }

    // Update position when corner changes AND not dragging
    // Use LaunchedEffect to avoid updating on every recomposition
    LaunchedEffect(corner, isDragging) {
        if (!isDragging) {
            dragStartImagePos = corner
        }
    }

    // Calculate screen position
    val screenX = dragStartImagePos.x * scale + offsetX
    val screenY = dragStartImagePos.y * scale + offsetY

    val touchTargetSize = 48.dp // Increased from 32dp for better usability
    val touchTargetSizePx = with(density) { touchTargetSize.toPx() }
    val halfTargetPx = touchTargetSizePx / 2f
    val offsetXDp = with(density) { (screenX - halfTargetPx).toDp() }
    val offsetYDp = with(density) { (screenY - halfTargetPx).toDp() }

    Box(
        modifier =
            Modifier
                .offset(x = offsetXDp, y = offsetYDp)
                .size(touchTargetSize)
                .semantics {
                    contentDescription = "${handle.name.replace("([A-Z])".toRegex(), " $1").trim()} corner handle. Drag to adjust document boundary"
                    role = Role.Button
                }
                .pointerInput(scale, offsetX, offsetY, halfTargetPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Mark as dragging to freeze Box position
                            isDragging = true
                            dragStartImagePos = corner

                            // Calculate absolute screen position for drag start
                            val currentScreenX = corner.x * scale + offsetX
                            val currentScreenY = corner.y * scale + offsetY
                            val absolute =
                                Offset(
                                    x = currentScreenX + offset.x - halfTargetPx,
                                    y = currentScreenY + offset.y - halfTargetPx,
                                )
                            onHandleDragStart(CropHandle.Corner(handle), absolute)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            // Use dragAmount to calculate new position relative to Box's starting position
                            // change.position is relative to Box at its START position (frozen during drag)
                            val boxStartScreenX = dragStartImagePos.x * scale + offsetX
                            val boxStartScreenY = dragStartImagePos.y * scale + offsetY

                            // Convert to absolute screen coordinates
                            val absoluteScreenX = boxStartScreenX + change.position.x - halfTargetPx
                            val absoluteScreenY = boxStartScreenY + change.position.y - halfTargetPx

                            onHandleDragUpdate(Offset(absoluteScreenX, absoluteScreenY))

                            // Convert absolute screen position to image coordinates
                            val imageX = (absoluteScreenX - offsetX) / scale
                            val imageY = (absoluteScreenY - offsetY) / scale

                            onDrag(Point(imageX, imageY))
                        },
                        onDragEnd = {
                            isDragging = false
                            onHandleDragEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            onHandleDragEnd()
                        },
                    )
                },
    )
}

@Composable
private fun DraggableSideHandles(
    edges: DocumentEdges,
    imageSize: ImageSize,
    rotation: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotatePointFn: (Point, Int, ImageSize) -> Point,
    inverseRotatePointFn: (Point, Int, ImageSize) -> Point,
    onEdgesChanged: (DocumentEdges) -> Unit,
    onHandleDragStart: (CropHandle, Offset) -> Unit,
    onHandleDragUpdate: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    // Use rememberUpdatedState to ensure lambda always reads latest edges value
    val currentEdges by rememberUpdatedState(edges)

    val rotatedImageSize =
        if (rotation == 90 || rotation == 270) {
            ImageSize(imageSize.height, imageSize.width)
        } else {
            imageSize
        }

    SideOrder.forEach { (sideId, indices) ->
        val (startIndex, endIndex) = indices
        val startCorner =
            when (startIndex) {
                0 -> currentEdges.topLeft
                1 -> currentEdges.topRight
                2 -> currentEdges.bottomRight
                else -> currentEdges.bottomLeft
            }
        val endCorner =
            when (endIndex) {
                0 -> currentEdges.topLeft
                1 -> currentEdges.topRight
                2 -> currentEdges.bottomRight
                else -> currentEdges.bottomLeft
            }

        val rotatedStart = rotatePointFn(startCorner, rotation, imageSize)
        val rotatedEnd = rotatePointFn(endCorner, rotation, imageSize)
        val midpoint =
            Point(
                x = (rotatedStart.x + rotatedEnd.x) / 2f,
                y = (rotatedStart.y + rotatedEnd.y) / 2f,
            )

        val isHorizontal = abs(rotatedEnd.x - rotatedStart.x) >= abs(rotatedEnd.y - rotatedStart.y)

        DraggableSideHandle(
            handle = sideId,
            midpoint = midpoint,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            imageSize = rotatedImageSize,
            isHorizontal = isHorizontal,
            onDrag = { delta ->
                val constrained =
                    if (isHorizontal) {
                        Point(0f, delta.y)
                    } else {
                        Point(delta.x, 0f)
                    }

                val newStart =
                    Point(
                        x = rotatedStart.x + constrained.x,
                        y = rotatedStart.y + constrained.y,
                    )
                val newEnd =
                    Point(
                        x = rotatedEnd.x + constrained.x,
                        y = rotatedEnd.y + constrained.y,
                    )

                val originalStart = inverseRotatePointFn(newStart, rotation, imageSize)
                val originalEnd = inverseRotatePointFn(newEnd, rotation, imageSize)

                val updatedEdges =
                    when (sideId) {
                        SideHandle.Top -> currentEdges.copy(topLeft = originalStart, topRight = originalEnd)
                        SideHandle.Right -> currentEdges.copy(topRight = originalStart, bottomRight = originalEnd)
                        SideHandle.Bottom -> currentEdges.copy(bottomRight = originalStart, bottomLeft = originalEnd)
                        SideHandle.Left -> currentEdges.copy(bottomLeft = originalStart, topLeft = originalEnd)
                    }
                onEdgesChanged(updatedEdges)
            },
            onHandleDragStart = onHandleDragStart,
            onHandleDragUpdate = onHandleDragUpdate,
            onHandleDragEnd = onHandleDragEnd,
        )
    }
}

@Composable
private fun DraggableSideHandle(
    handle: SideHandle,
    midpoint: Point,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    imageSize: ImageSize,
    isHorizontal: Boolean,
    onDrag: (Point) -> Unit,
    onHandleDragStart: (CropHandle, Offset) -> Unit,
    onHandleDragUpdate: (Offset) -> Unit,
    onHandleDragEnd: () -> Unit,
) {
    val density = LocalDensity.current

    // Track drag state
    var isDragging by remember { mutableStateOf(false) }

    // Track position - updates from midpoint only when NOT dragging
    var dragStartImagePos by remember { mutableStateOf(midpoint) }

    // Update position when midpoint changes AND not dragging
    // Use LaunchedEffect to avoid updating on every recomposition
    LaunchedEffect(midpoint, isDragging) {
        if (!isDragging) {
            dragStartImagePos = midpoint
        }
    }

    // Calculate screen position
    val screenX = dragStartImagePos.x * scale + offsetX
    val screenY = dragStartImagePos.y * scale + offsetY

    val touchTargetSize = 48.dp
    val touchTargetSizePx = with(density) { touchTargetSize.toPx() }
    val halfTargetPx = touchTargetSizePx / 2f
    val offsetXDp = with(density) { (screenX - halfTargetPx).toDp() }
    val offsetYDp = with(density) { (screenY - halfTargetPx).toDp() }

    Box(
        modifier =
            Modifier
                .offset(x = offsetXDp, y = offsetYDp)
                .size(touchTargetSize)
                .pointerInput(scale, offsetX, offsetY, halfTargetPx, isHorizontal) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Mark as dragging to freeze Box position
                            isDragging = true
                            dragStartImagePos = midpoint

                            // Calculate absolute screen position for drag start
                            val currentScreenX = midpoint.x * scale + offsetX
                            val currentScreenY = midpoint.y * scale + offsetY
                            val absolute =
                                Offset(
                                    x = currentScreenX + offset.x - halfTargetPx,
                                    y = currentScreenY + offset.y - halfTargetPx,
                                )
                            onHandleDragStart(CropHandle.Side(handle), absolute)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            // Use dragAmount to calculate new position relative to Box's starting position
                            // change.position is relative to Box at its START position (frozen during drag)
                            val boxStartScreenX = dragStartImagePos.x * scale + offsetX
                            val boxStartScreenY = dragStartImagePos.y * scale + offsetY

                            // Convert to absolute screen coordinates
                            val absoluteScreenX = boxStartScreenX + change.position.x - halfTargetPx
                            val absoluteScreenY = boxStartScreenY + change.position.y - halfTargetPx

                            onHandleDragUpdate(Offset(absoluteScreenX, absoluteScreenY))

                            // Convert absolute screen position to image coordinates
                            val imageX = (absoluteScreenX - offsetX) / scale
                            val imageY = (absoluteScreenY - offsetY) / scale

                            // Calculate delta from initial midpoint for constraint logic
                            val deltaX = if (isHorizontal) 0f else (imageX - dragStartImagePos.x)
                            val deltaY = if (isHorizontal) (imageY - dragStartImagePos.y) else 0f

                            onDrag(Point(deltaX, deltaY))
                        },
                        onDragEnd = {
                            isDragging = false
                            onHandleDragEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            onHandleDragEnd()
                        },
                    )
                },
    )
}

private fun DrawScope.drawCropShape(
    screenCorners: List<Offset>,
    showCropHandles: Boolean,
    showGlowAnimation: Boolean,
    glowAlpha: Float,
    activeCorner: CornerHandle?,
    activeSide: SideHandle?,
) {
    if (screenCorners.size < 4) return

    val path =
        Path().apply {
            moveTo(screenCorners[0].x, screenCorners[0].y)
            screenCorners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }

    drawPath(
        path = path,
        color = Color.Cyan,
        style = Stroke(width = 4f),
    )

    if (showGlowAnimation) {
        drawPath(
            path = path,
            color = Color.Cyan.copy(alpha = glowAlpha * 0.5f),
            style = Stroke(width = 8f),
        )
    }

    if (!showCropHandles) return

    CornerOrder.forEachIndexed { index, cornerId ->
        val corner = screenCorners[index]
        val isActive = cornerId == activeCorner
        val outerRadius = 12.dp.toPx()
        val innerRadius = 9.dp.toPx()

        drawCircle(
            color = if (isActive) Color.Cyan else Color.White,
            radius = outerRadius,
            center = corner,
        )
        drawCircle(
            color = if (isActive) Color.White else Color.Cyan,
            radius = outerRadius,
            center = corner,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = if (isActive) Color.White else Color.Cyan,
            radius = innerRadius,
            center = corner,
        )
    }

    SideOrder.forEach { (sideId, indices) ->
        val (startIndex, endIndex) = indices
        val start = screenCorners[startIndex]
        val end = screenCorners[endIndex]
        val isActive = sideId == activeSide

        val midpoint =
            Offset(
                x = (start.x + end.x) / 2f,
                y = (start.y + end.y) / 2f,
            )

        val angleRad = kotlin.math.atan2(end.y - start.y, end.x - start.x)
        val cos = kotlin.math.cos(angleRad).toFloat()
        val sin = kotlin.math.sin(angleRad).toFloat()
        val handleWidth = 40.dp.toPx()
        val handleHeight = 12.dp.toPx()
        val halfWidth = handleWidth / 2f
        val halfHeight = handleHeight / 2f

        val corners =
            listOf(
                Offset(-halfWidth, -halfHeight),
                Offset(halfWidth, -halfHeight),
                Offset(halfWidth, halfHeight),
                Offset(-halfWidth, halfHeight),
            ).map { corner ->
                Offset(
                    x = midpoint.x + corner.x * cos - corner.y * sin,
                    y = midpoint.y + corner.x * sin + corner.y * cos,
                )
            }

        val handlePath =
            Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }

        drawPath(
            path = handlePath,
            color = if (isActive) Color.Cyan else Color.White,
        )

        drawPath(
            path = handlePath,
            color = if (isActive) Color.White else Color.Cyan,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun insetEdges(
    edges: DocumentEdges,
    imageSize: ImageSize,
    insetRatio: Float,
): DocumentEdges {
    val insetX = imageSize.width * insetRatio
    val insetY = imageSize.height * insetRatio

    return DocumentEdges(
        topLeft =
            Point(
                x = edges.topLeft.x + insetX,
                y = edges.topLeft.y + insetY,
            ),
        topRight =
            Point(
                x = edges.topRight.x - insetX,
                y = edges.topRight.y + insetY,
            ),
        bottomRight =
            Point(
                x = edges.bottomRight.x - insetX,
                y = edges.bottomRight.y - insetY,
            ),
        bottomLeft =
            Point(
                x = edges.bottomLeft.x + insetX,
                y = edges.bottomLeft.y - insetY,
            ),
        confidence = edges.confidence,
        mode = edges.mode,
    )
}

/**
 * Validates that the crop bounds form a valid quadrilateral without crossing edges.
 * Uses the cross product to check if corners are in correct order (clockwise or counter-clockwise).
 *
 * @return true if the edges form a valid quadrilateral, false if edges cross or corners are invalid
 */
private fun isValidQuadrilateral(edges: DocumentEdges): Boolean {
    // Calculate area using the shoelace formula
    // If the result is negative or too small, the quadrilateral is invalid (self-intersecting or degenerate)
    val area =
        0.5 *
            abs(
                (edges.topLeft.x * edges.topRight.y - edges.topRight.x * edges.topLeft.y) +
                    (edges.topRight.x * edges.bottomRight.y - edges.bottomRight.x * edges.topRight.y) +
                    (edges.bottomRight.x * edges.bottomLeft.y - edges.bottomLeft.x * edges.bottomRight.y) +
                    (edges.bottomLeft.x * edges.topLeft.y - edges.topLeft.x * edges.bottomLeft.y),
            )

    // Minimum area threshold (100 square pixels) to prevent degenerate quadrilaterals
    val minArea = 100.0
    if (area < minArea) {
        return false
    }

    // Check that all corners are distinct (minimum distance between any two corners)
    val minDistance = 10.0 // pixels
    val corners = listOf(edges.topLeft, edges.topRight, edges.bottomRight, edges.bottomLeft)

    for (i in corners.indices) {
        for (j in i + 1 until corners.size) {
            val dx = corners[i].x - corners[j].x
            val dy = corners[i].y - corners[j].y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance < minDistance) {
                return false // Corners too close together
            }
        }
    }

    // Check for edge intersections using line segment intersection test
    // Edges: TL-TR, TR-BR, BR-BL, BL-TL
    // Test opposite edges for intersection (TL-TR vs BR-BL, TR-BR vs BL-TL)

    fun doSegmentsIntersect(
        p1: Point,
        p2: Point,
        p3: Point,
        p4: Point,
    ): Boolean {
        val d1 = (p2.x - p1.x) * (p3.y - p1.y) - (p2.y - p1.y) * (p3.x - p1.x)
        val d2 = (p2.x - p1.x) * (p4.y - p1.y) - (p2.y - p1.y) * (p4.x - p1.x)
        val d3 = (p4.x - p3.x) * (p1.y - p3.y) - (p4.y - p3.y) * (p1.x - p3.x)
        val d4 = (p4.x - p3.x) * (p2.y - p3.y) - (p4.y - p3.y) * (p2.x - p3.x)

        // Segments intersect if signs differ (excluding endpoints)
        return (d1 * d2 < 0) && (d3 * d4 < 0)
    }

    // Check if opposite edges intersect (which would make it invalid)
    if (doSegmentsIntersect(edges.topLeft, edges.topRight, edges.bottomRight, edges.bottomLeft)) {
        return false // Top edge crosses bottom edge
    }
    if (doSegmentsIntersect(edges.topRight, edges.bottomRight, edges.bottomLeft, edges.topLeft)) {
        return false // Right edge crosses left edge
    }

    return true
}
