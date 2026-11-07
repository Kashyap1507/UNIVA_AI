package com.kashyap.wizlyai.util

import com.kashyap.wizlyai.ios.IOSCallbackBridge

/**
 * iOS implementation of GalleryPicker
 * Uses PHPickerViewController via Swift bridge (iOS 14+)
 */
actual class GalleryPicker {
    actual fun pickImage(
        onImageSelected: (String) -> Unit,
        onCancelled: () -> Unit,
    ) {
        val callbacks = IOSCallbackBridge.getGalleryPickerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS GalleryPicker: Callbacks not set, calling onCancelled")
            onCancelled()
            return
        }
        callbacks.pickImage(onImageSelected, onCancelled)
    }

    actual fun pickMultipleImages(
        maxImages: Int,
        onImagesSelected: (List<String>) -> Unit,
        onCancelled: () -> Unit,
    ) {
        val callbacks = IOSCallbackBridge.getGalleryPickerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS GalleryPicker: Callbacks not set, calling onCancelled")
            onCancelled()
            return
        }
        callbacks.pickMultipleImages(maxImages, onImagesSelected, onCancelled)
    }

    actual fun isAvailable(): Boolean {
        val callbacks = IOSCallbackBridge.getGalleryPickerCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS GalleryPicker: Callbacks not set, returning false")
            return false
        }
        return callbacks.isAvailable()
    }
}
