package com.kashyap.wizlyai.util

import androidx.compose.ui.geometry.Offset

/**
 * iOS stub implementation of ImageCropper
 * TODO: Implement using CoreImage CIPerspectiveTransform
 */
actual class ImageCropper {
    actual fun cropWithPerspective(
        inputPath: String,
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset,
    ): String? {
        // TODO: Implement iOS perspective transform using CoreImage
        println("ImageCropper.cropWithPerspective not yet implemented on iOS")
        return null
    }

    actual fun cropRectangle(
        inputPath: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): String? {
        // TODO: Implement iOS rectangular crop using CGImage
        println("ImageCropper.cropRectangle not yet implemented on iOS")
        return null
    }
}
