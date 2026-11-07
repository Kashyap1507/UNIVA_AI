package com.kashyap.wizlyai.util

/**
 * Common interface for picking images from device gallery
 */
expect class GalleryPicker {
    /**
     * Launch gallery picker
     * @param onImageSelected Callback with selected image path
     * @param onCancelled Callback when user cancels
     */
    fun pickImage(
        onImageSelected: (String) -> Unit,
        onCancelled: () -> Unit,
    )

    /**
     * Pick multiple images from gallery
     * @param maxImages Maximum number of images to select
     * @param onImagesSelected Callback with list of selected image paths
     * @param onCancelled Callback when user cancels
     */
    fun pickMultipleImages(
        maxImages: Int = 10,
        onImagesSelected: (List<String>) -> Unit,
        onCancelled: () -> Unit,
    )

    /**
     * Check if gallery access is available
     */
    fun isAvailable(): Boolean
}
