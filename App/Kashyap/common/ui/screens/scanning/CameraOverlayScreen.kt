package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.camera.CameraController
import com.kashyap.wizlyai.ui.components.*
import com.kashyap.wizlyai.ui.components.CameraPreview
import com.kashyap.wizlyai.util.PermissionManager
import com.kashyap.wizlyai.util.PermissionState
import com.kashyap.wizlyai.util.getUserMessage

/**
 * Camera Overlay Screen - Modern camera UI with permission handling
 * - Clean minimal design with right-angle corner brackets
 * - Ring-style red capture button
 * - Flash toggle and gallery access
 * - Permission flow with rationale
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraOverlayScreen(
    onNavigateHome: () -> Unit,
    onCapturePhoto: (String) -> Unit,
    onSelectFromGallery: (String) -> Unit,
    permissionManager: PermissionManager,
    galleryPicker: com.kashyap.wizlyai.util.GalleryPicker,
    cameraController: CameraController,
    modifier: Modifier = Modifier,
) {
    var permissionState by remember { mutableStateOf(PermissionState.NOT_REQUESTED) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    // POLISH-004: Gallery import state
    var isImportingFromGallery by remember { mutableStateOf(false) }
    var showGalleryError by remember { mutableStateOf(false) }
    var galleryErrorMessage by remember { mutableStateOf("") }
    var showFileSizeWarning by remember { mutableStateOf(false) }
    val imageValidator = remember { com.kashyap.wizlyai.util.ImageValidator() }
    val haptic = com.kashyap.wizlyai.util.rememberHapticFeedback()

    // Check permission on screen load
    LaunchedEffect(Unit) {
        permissionState =
            when {
                permissionManager.isCameraPermissionGranted() -> {
                    isCameraReady = true
                    PermissionState.GRANTED
                }
                permissionManager.shouldShowCameraRationale() -> {
                    showPermissionRationale = true
                    PermissionState.DENIED
                }
                else -> PermissionState.NOT_REQUESTED
            }

        // Auto-request if not requested yet
        if (permissionState == PermissionState.NOT_REQUESTED) {
            permissionManager.requestCameraPermission(
                onGranted = {
                    permissionState = PermissionState.GRANTED
                    isCameraReady = true
                },
                onDenied = {
                    permissionState =
                        if (permissionManager.shouldShowCameraRationale()) {
                            showPermissionRationale = true
                            PermissionState.DENIED
                        } else {
                            PermissionState.PERMANENTLY_DENIED
                        }
                },
            )
        }
    }

    // Permission Rationale Dialog
    if (showPermissionRationale) {
        ThemedAlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = {
                Text(
                    "Camera Permission Required",
                    modifier = Modifier.heading(),
                )
            },
            text = { Text("This app needs camera access to scan your work and course documents. Please grant camera permission to continue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        permissionManager.requestCameraPermission(
                            onGranted = {
                                permissionState = PermissionState.GRANTED
                                isCameraReady = true
                            },
                            onDenied = {
                                permissionState = PermissionState.PERMANENTLY_DENIED
                            },
                        )
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Grant camera permission to scan documents"
                        },
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        onNavigateHome()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Cancel and return to home screen"
                        },
                ) {
                    Text("Cancel")
                }
            },
            modifier =
                Modifier.semantics {
                    contentDescription = "Camera permission required dialog"
                },
        )
    }

    // Permanently Denied State
    if (permissionState == PermissionState.PERMANENTLY_DENIED) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Camera permission denied. Please enable camera permission in app settings."
                        liveRegion = LiveRegionMode.Polite
                    },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .decorative(),
                    tint = Color.Gray,
                )
                Text(
                    "Camera Permission Denied",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.heading(),
                )
                Text(
                    "Please enable camera permission in app settings to scan documents.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color.Gray,
                )
                Button(
                    onClick = { permissionManager.openAppSettings() },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Open app settings to enable camera permission"
                        },
                ) {
                    Text("Open Settings")
                }
                TextButton(
                    onClick = onNavigateHome,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Return to home screen"
                        },
                ) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black), // Pure black for camera
    ) {
        // Camera Preview - Live feed
        if (isCameraReady) {
            CameraPreview(
                cameraController = cameraController,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Minimal Top Bar - Close button only (no title)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Close button (navigate to Home)
            IconButton(
                onClick = onNavigateHome,
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape,
                        )
                        .semantics {
                            contentDescription = "Close camera and return to home screen"
                        },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                )
            }

            // Flash toggle
            IconButton(
                onClick = {
                    flashEnabled = !flashEnabled
                    cameraController.setFlashEnabled(flashEnabled)
                },
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape,
                        )
                        .semantics {
                            stateDescription = if (flashEnabled) "Flash is on" else "Flash is off"
                            contentDescription =
                                if (flashEnabled) {
                                    "Turn flash off for camera"
                                } else {
                                    "Turn flash on for camera"
                                }
                        },
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = null,
                    tint = if (flashEnabled) Color(0xFFFFD700) else Color.White,
                )
            }
        }

        // No overlay on camera preview - clean view

        // Bottom controls
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding() // iOS safe area - bottom inset for home indicator
                    .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Gallery button (top-left of capture button)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gallery button
                IconButton(
                    onClick = {
                        isImportingFromGallery = true
                        galleryPicker.pickImage(
                            onImageSelected = { imagePath ->
                                println("Gallery: Image selected - $imagePath")

                                // POLISH-004 & POLISH-013: Validate image before processing
                                val validationResult = imageValidator.validateImage(imagePath)
                                println("Gallery: Validation result - $validationResult")

                                when (validationResult) {
                                    is com.kashyap.wizlyai.util.ImageValidationResult.Valid -> {
                                        // Check for file size warning (> 10 MB)
                                        if (validationResult.fileSize > com.kashyap.wizlyai.util.ImageValidationConstants.WARNING_FILE_SIZE_BYTES) {
                                            showFileSizeWarning = true
                                            galleryErrorMessage = "Large image detected (${validationResult.fileSize / 1_000_000} MB). Processing may take longer."
                                        }

                                        // Trigger haptic on successful selection
                                        haptic.performLight()

                                        // Image is valid - proceed
                                        isImportingFromGallery = false
                                        onSelectFromGallery(imagePath)
                                    }
                                    else -> {
                                        // Show error dialog
                                        galleryErrorMessage = validationResult.getUserMessage()
                                        showGalleryError = true
                                        isImportingFromGallery = false
                                    }
                                }
                            },
                            onCancelled = {
                                println("Gallery: Selection cancelled")
                                isImportingFromGallery = false
                            },
                        )
                    },
                    enabled = !isImportingFromGallery,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription =
                                    if (isImportingFromGallery) {
                                        "Loading image from gallery"
                                    } else {
                                        "Select image from photo library"
                                    }
                                if (isImportingFromGallery) {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            },
                ) {
                    if (isImportingFromGallery) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Capture Button - Ring-style with animated loading state
                CaptureButton(
                    isCapturing = isCapturing,
                    flashEnabled = flashEnabled,
                    onClick = {
                        if (!isCapturing) {
                            println("CameraOverlay: Capture button clicked")
                            isCapturing = true
                            cameraController.capturePhoto(
                                onSuccess = { imagePath ->
                                    println("CameraOverlay: Photo captured successfully: $imagePath")
                                    onCapturePhoto(imagePath)
                                    // Reset after navigation
                                    isCapturing = false
                                },
                                onError = { error ->
                                    println("CameraOverlay: Camera capture error: $error")
                                    isCapturing = false
                                },
                            )
                        }
                    },
                )

                // Spacer to balance layout
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help text
            Text(
                text = "Tap to scan or use gallery",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // POLISH-004: Gallery error dialog
        if (showGalleryError) {
            ThemedAlertDialog(
                onDismissRequest = { showGalleryError = false },
                title = {
                    Text(
                        "Image Error",
                        modifier = Modifier.heading(),
                    )
                },
                text = { Text(galleryErrorMessage) },
                confirmButton = {
                    TextButton(
                        onClick = { showGalleryError = false },
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Dismiss image error dialog"
                            },
                    ) {
                        Text("OK", color = Color.Cyan)
                    }
                },
                modifier = Modifier.errorSemantics(galleryErrorMessage),
            )
        }

        // POLISH-004: File size warning (non-blocking)
        if (showFileSizeWarning) {
            ThemedAlertDialog(
                onDismissRequest = { showFileSizeWarning = false },
                title = {
                    Text(
                        "Large Image",
                        modifier = Modifier.heading(),
                    )
                },
                text = { Text(galleryErrorMessage) },
                confirmButton = {
                    TextButton(
                        onClick = { showFileSizeWarning = false },
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Dismiss file size warning"
                            },
                    ) {
                        Text("OK", color = Color.Cyan)
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.decorative(),
                        tint = Color(0xFFFFD700),
                    )
                },
                modifier =
                    Modifier.semantics {
                        contentDescription = "Warning: Large image detected. Processing may take longer."
                    },
            )
        }
    }
}

/**
 * Capture button with animated loading state
 */
@Composable
private fun CaptureButton(
    isCapturing: Boolean,
    flashEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animated inner circle opacity
    val innerAlpha by animateFloatAsState(
        targetValue = if (isCapturing) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "innerAlpha",
    )

    // Rotating animation for loading state
    val infiniteTransition = rememberInfiniteTransition(label = "captureRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotationAngle",
    )

    Box(
        modifier =
            modifier
                .size(80.dp)
                .clickable(
                    onClick = onClick,
                    indication = null, // Remove ripple effect
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                )
                .semantics {
                    contentDescription =
                        if (isCapturing) {
                            "Capturing photo, please wait"
                        } else {
                            val flashState = if (flashEnabled) "with flash" else "without flash"
                            "Capture photo $flashState"
                        }
                    if (isCapturing) {
                        liveRegion = LiveRegionMode.Polite
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Outer white ring
            drawCircle(
                color = Color.White,
                radius = 38.dp.toPx(),
                style = Stroke(width = 4.dp.toPx()),
            )

            // Animated loading arc (only visible when capturing)
            if (isCapturing) {
                val arcLength = 90f
                drawArc(
                    color = Color(0xFFE53935),
                    startAngle = rotationAngle,
                    sweepAngle = arcLength,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    size =
                        androidx.compose.ui.geometry.Size(
                            width = 76.dp.toPx(),
                            height = 76.dp.toPx(),
                        ),
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                )
            }

            // Inner filled red circle with animated opacity
            drawCircle(
                color = Color(0xFFE53935).copy(alpha = innerAlpha),
                radius = 30.dp.toPx(),
            )
        }
    }
}
