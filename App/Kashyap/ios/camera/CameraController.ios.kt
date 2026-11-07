package com.kashyap.wizlyai.camera

import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS Camera implementation using AVFoundation
 * Bridges to Swift IOSCameraController which uses AVCaptureSession
 */
actual class CameraController {
    private var isFlashOn: Boolean = false

    actual fun initialize(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
    ) {
        println("📸 iOS CameraController: Checking camera permission")

        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS CameraController: Callbacks not set, assuming permission granted")
            onPermissionGranted()
            return
        }

        callbacks.checkCameraPermission { granted ->
            if (granted) {
                println("✅ iOS CameraController: Permission granted")
                onPermissionGranted()
            } else {
                println("❌ iOS CameraController: Permission denied")
                onPermissionDenied()
            }
        }
    }

    actual fun startPreview() {
        println("📸 iOS CameraController: Starting preview")

        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS CameraController: Callbacks not set")
            return
        }

        callbacks.startPreview()
    }

    actual fun stopPreview() {
        println("📸 iOS CameraController: Stopping preview")

        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS CameraController: Callbacks not set")
            return
        }

        callbacks.stopPreview()
    }

    actual fun capturePhoto(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        println("📸 iOS CameraController: Capturing photo")

        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("❌ iOS CameraController: Callbacks not set")
            onError("Camera not initialized")
            return
        }

        callbacks.capturePhoto(
            { imagePath ->
                println("✅ iOS CameraController: Photo captured at $imagePath")
                onSuccess(imagePath)
            },
            { errorMessage ->
                println("❌ iOS CameraController: Photo capture failed: $errorMessage")
                onError(errorMessage)
            },
        )
    }

    actual fun hasPermission(): Boolean {
        println("📸 iOS CameraController: Checking permission")

        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS CameraController: Callbacks not set, returning true")
            return true
        }

        // Note: This is synchronous, but checkCameraPermission is async
        // For now, assume we have permission if callbacks are set
        // The proper check happens in initialize()
        return true
    }

    actual fun setFlashEnabled(enabled: Boolean) {
        println("💡 iOS CameraController: Setting flash to ${if (enabled) "ON" else "OFF"}")

        isFlashOn = enabled

        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS CameraController: Callbacks not set")
            return
        }

        callbacks.setFlashEnabled(enabled)
    }

    actual fun isFlashEnabled(): Boolean {
        val callbacks = IOSCallbackBridge.getCameraCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS CameraController: Callbacks not set, returning cached value")
            return isFlashOn
        }

        return callbacks.isFlashEnabled()
    }

    actual fun release() {
        println("📸 iOS CameraController: Releasing camera resources")
        stopPreview()
    }
}
