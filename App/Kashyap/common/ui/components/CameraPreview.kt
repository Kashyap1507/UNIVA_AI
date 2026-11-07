package com.kashyap.wizlyai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kashyap.wizlyai.camera.CameraController

/**
 * Camera Preview Composable - Platform-specific implementation
 * Displays live camera feed for document scanning
 */
@Composable
expect fun CameraPreview(
    cameraController: CameraController,
    modifier: Modifier = Modifier,
)
