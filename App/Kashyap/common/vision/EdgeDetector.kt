package com.kashyap.wizlyai.vision

/**
 * Cross-platform edge detector for document scanning.
 *
 * Android: Full OpenCV implementation with auto-detection
 * iOS: Stub implementation (manual mode only)
 */
expect class EdgeDetector() {
    /**
     * Automatically detect document edges in an image.
     *
     * @param imagePath Absolute file path to the image
     * @return DocumentEdges if detection successful, null if no document found (triggers manual mode)
     */
    suspend fun detectEdges(imagePath: String): DocumentEdges?

    /**
     * Apply perspective transform to crop and straighten the document.
     *
     * @param imagePath Path to original image
     * @param corners The four corners of the document (null to skip transform)
     * @param rotation Rotation angle (0, 90, 180, 270 degrees)
     * @return Path to the transformed/cropped image (or original path if corners is null)
     */
    suspend fun applyPerspectiveTransform(
        imagePath: String,
        corners: DocumentEdges?,
        rotation: Int,
    ): String

    /**
     * Get default corners for manual mode (full image bounds with small margin).
     *
     * @param imageWidth Width of the image in pixels
     * @param imageHeight Height of the image in pixels
     * @return DocumentEdges with corners at image boundaries
     */
    fun getDefaultCorners(
        imageWidth: Int,
        imageHeight: Int,
    ): DocumentEdges

    /**
     * Get image dimensions without loading the full image.
     *
     * @param imagePath Path to the image file
     * @return ImageSize with width and height
     */
    fun getImageSize(imagePath: String): ImageSize
}
