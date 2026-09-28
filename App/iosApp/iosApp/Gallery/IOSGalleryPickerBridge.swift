import Foundation
import UIKit
import shared

/**
 * Swift bridge to connect IOSGalleryPicker to Kotlin
 * Provides callback-based API for Kotlin gallery access
 */
@MainActor
@objc public class IOSGalleryPickerBridge: NSObject {

    private static let galleryPicker = IOSGalleryPicker.shared

    // MARK: - Single Image Selection

    /**
     * Pick a single image from photo library
     * @param onImageSelected: Callback with file path when image is selected
     * @param onCancelled: Callback when user cancels
     */
    @objc public static func pickImage(
        onImageSelected: @escaping (String) -> Void,
        onCancelled: @escaping () -> Void
    ) {
        galleryPicker.pickImage(
            onImageSelected: onImageSelected,
            onCancelled: onCancelled
        )
    }

    // MARK: - Multiple Image Selection

    /**
     * Pick multiple images from photo library
     * @param maxImages: Maximum number of images to select
     * @param onImagesSelected: Callback with file paths array when images are selected
     * @param onCancelled: Callback when user cancels
     */
    @objc public static func pickMultipleImages(
        maxImages: Int,
        onImagesSelected: @escaping ([String]) -> Void,
        onCancelled: @escaping () -> Void
    ) {
        galleryPicker.pickMultipleImages(
            maxImages: maxImages,
            onImagesSelected: onImagesSelected,
            onCancelled: onCancelled
        )
    }

    // MARK: - Availability Check

    /**
     * Check if photo library is available
     * Always returns true on iOS 14+
     */
    @objc public static func isAvailable() -> Bool {
        if #available(iOS 14, *) {
            return true
        } else {
            print("⚠️ IOSGalleryPickerBridge: PHPickerViewController requires iOS 14+")
            return false
        }
    }
}
