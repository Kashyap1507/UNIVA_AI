package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.kashyap.wizlyai.camera.CameraController
import com.kashyap.wizlyai.ios.IOSCallbackBridge
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.UIKit.UIScreen

/**
 * iOS implementation of CameraPreview using AVFoundation
 * Displays live camera feed using setupCameraPreview callback
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    cameraController: CameraController,
    modifier: Modifier,
) {
    DisposableEffect(Unit) {
        println("📸 CameraPreview.ios: Starting camera preview")
        cameraController.startPreview()

        onDispose {
            println("⏹️ CameraPreview.ios: Stopping camera preview")
            cameraController.stopPreview()
        }
    }

    // Use UIKitView to embed the camera preview view
    UIKitView(
        factory = {
            println("🏭 CameraPreview.ios: Setting up camera preview")

            val callbacks = IOSCallbackBridge.getCameraCallbacks()
            if (callbacks != null) {
                // Get screen dimensions
                val bounds = UIScreen.mainScreen.bounds
                val screenWidth = CGRectGetWidth(bounds)
                val screenHeight = CGRectGetHeight(bounds)

                println("   Screen size: ${screenWidth}x$screenHeight")

                // Call setupCameraPreview to get UIView with camera preview
                val previewView = callbacks.setupCameraPreview(screenWidth, screenHeight)
                println("✅ CameraPreview.ios: Camera preview view created")
                previewView
            } else {
                println("❌ CameraPreview.ios: Camera callbacks not set")
                // Return a black view as fallback
                platform.UIKit.UIView().apply {
                    backgroundColor = platform.UIKit.UIColor.blackColor
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
