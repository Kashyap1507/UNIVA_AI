package com.kashyap.wizlyai.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kashyap.wizlyai.camera.CameraController

/**
 * Android implementation of CameraPreview using CameraX
 * Displays live camera feed in an AndroidView wrapper
 */
@Composable
actual fun CameraPreview(
    cameraController: CameraController,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Set scale type to fill view
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = modifier,
        update = { previewView ->
            // Pass PreviewView to CameraController
            (cameraController as? com.kashyap.wizlyai.camera.CameraController)?.setPreviewView(previewView)
        },
    )

    // Start preview when composable enters composition
    DisposableEffect(cameraController) {
        cameraController.startPreview()

        onDispose {
            cameraController.stopPreview()
        }
    }
}
