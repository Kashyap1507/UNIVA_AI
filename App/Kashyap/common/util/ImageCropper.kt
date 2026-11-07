package com.kashyap.wizlyai.util

import androidx.compose.ui.geometry.Offset

/**
 * Image cropping utility for document scanning
 * Supports perspective transformation based on four corner points
 */
expect class ImageCropper {
    /**
     * Crop image with perspective transform
     * @param inputPath Path to input image file
     * @param topLeft Normalized top-left corner (0.0 to 1.0)
     * @param topRight Normalized top-right corner (0.0 to 1.0)
     * @param bottomLeft Normalized bottom-left corner (0.0 to 1.0)
     * @param bottomRight Normalized bottom-right corner (0.0 to 1.0)
     * @return Path to cropped image file, or null if cropping failed
     */
    fun cropWithPerspective(
        inputPath: String,
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset,
    ): String?

    /**
     * Simple rectangular crop without perspective transform
     * @param inputPath Path to input image file
     * @param left Normalized left edge (0.0 to 1.0)
     * @param top Normalized top edge (0.0 to 1.0)
     * @param right Normalized right edge (0.0 to 1.0)
     * @param bottom Normalized bottom edge (0.0 to 1.0)
     * @return Path to cropped image file, or null if cropping failed
     */
    fun cropRectangle(
        inputPath: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): String?
}

/**
 * Crop configuration
 */
data class CropConfig(
    val outputQuality: Int = 90,
    val outputFormat: ImageFormat = ImageFormat.JPEG,
)

enum class ImageFormat {
    JPEG,
    PNG,
}
