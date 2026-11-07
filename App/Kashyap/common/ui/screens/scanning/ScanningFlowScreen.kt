package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Scanning flow for "Check my work": camera (or gallery) → photo review with edge detection.
 * Each accepted photo is added to the review list and the app moves on to the ScanReview screen.
 */
@Composable
fun ScanningFlowScreen(
    onBack: () -> Unit,
    onNavigateToScanReview: () -> Unit,
    permissionManager: com.kashyap.wizlyai.util.PermissionManager,
    galleryPicker: com.kashyap.wizlyai.util.GalleryPicker,
    cameraController: com.kashyap.wizlyai.camera.CameraController,
    imageEnhancer: com.kashyap.wizlyai.util.ImageEnhancer? = null,
    localStorage: com.kashyap.wizlyai.data.repository.LocalDocumentStorage? = null,
    preferencesManager: com.kashyap.wizlyai.data.preferences.PreferencesManager? = null,
    externalViewModel: com.kashyap.wizlyai.ui.viewmodel.ScanningViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel =
        externalViewModel ?: remember {
            com.kashyap.wizlyai.ui.viewmodel.ScanningViewModel(
                localStorage = localStorage ?: com.kashyap.wizlyai.data.repository.NoOpLocalDocumentStorage(),
                viewModelScope = coroutineScope,
            )
        }

    var currentStage by remember { mutableStateOf(ScanningStage.CAMERA) }
    var capturedImagePath by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 300))
            },
        ) { stage ->
            when (stage) {
                ScanningStage.CAMERA -> {
                    CameraOverlayScreen(
                        onNavigateHome = onBack,
                        onCapturePhoto = { imagePath ->
                            capturedImagePath = imagePath
                            currentStage = ScanningStage.CAPTURE
                        },
                        onSelectFromGallery = { imagePath ->
                            capturedImagePath = imagePath
                            currentStage = ScanningStage.CAPTURE
                        },
                        permissionManager = permissionManager,
                        galleryPicker = galleryPicker,
                        cameraController = cameraController,
                    )
                }

                ScanningStage.CAPTURE -> {
                    // The photo review overlay below covers this; black keeps the transition smooth
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                    )
                }
            }
        }

        val imagePath = capturedImagePath
        if (imagePath != null && preferencesManager != null) {
            AnimatedVisibility(
                visible = currentStage == ScanningStage.CAPTURE,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
            ) {
                PhotoReviewScreenWithEdgeDetection(
                    imagePath = imagePath,
                    onRetake = {
                        capturedImagePath = null
                        currentStage = ScanningStage.CAMERA
                    },
                    onUsePhoto = { finalImagePath, _, _ ->
                        viewModel.addReviewImage(finalImagePath)
                        onNavigateToScanReview()
                    },
                    preferencesManager = preferencesManager,
                    imageEnhancer = imageEnhancer,
                )
            }
        }
    }
}

enum class ScanningStage {
    CAMERA,
    CAPTURE,
}
