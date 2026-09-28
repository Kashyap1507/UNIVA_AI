import Foundation
import UIKit
import PhotosUI
import UniformTypeIdentifiers

/**
 * iOS Gallery Picker using PHPickerViewController
 * Modern iOS 14+ API for photo selection
 */
@MainActor
class IOSGalleryPicker: NSObject {

    static let shared = IOSGalleryPicker()

    private var onImageSelected: ((String) -> Void)?
    private var onImagesSelected: (([String]) -> Void)?
    private var onCancelled: (() -> Void)?

    private override init() {
        super.init()
    }

    // MARK: - Single Image Selection

    /**
     * Pick a single image from photo library
     * @param onImageSelected: Callback with file path when image is selected
     * @param onCancelled: Callback when user cancels
     */
    func pickImage(
        onImageSelected: @escaping (String) -> Void,
        onCancelled: @escaping () -> Void
    ) {
        print("📸 IOSGalleryPicker: Starting single image picker...")

        self.onImageSelected = onImageSelected
        self.onCancelled = onCancelled

        // Configure PHPickerViewController for single selection
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.selectionLimit = 1
        configuration.filter = .images // Only show images, no videos
        configuration.preferredAssetRepresentationMode = .current

        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self

        // Present from root view controller
        DispatchQueue.main.async {
            self.presentPicker(picker)
        }
    }

    // MARK: - Multiple Image Selection

    /**
     * Pick multiple images from photo library
     * @param maxImages: Maximum number of images to select
     * @param onImagesSelected: Callback with file paths when images are selected
     * @param onCancelled: Callback when user cancels
     */
    func pickMultipleImages(
        maxImages: Int,
        onImagesSelected: @escaping ([String]) -> Void,
        onCancelled: @escaping () -> Void
    ) {
        print("📸 IOSGalleryPicker: Starting multiple image picker (max: \(maxImages))...")

        self.onImagesSelected = onImagesSelected
        self.onCancelled = onCancelled

        // Configure PHPickerViewController for multiple selection
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.selectionLimit = maxImages
        configuration.filter = .images
        configuration.preferredAssetRepresentationMode = .current

        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self

        // Present from root view controller
        DispatchQueue.main.async {
            self.presentPicker(picker)
        }
    }

    // MARK: - Helper Methods

    /**
     * Present picker from root view controller
     */
    private func presentPicker(_ picker: PHPickerViewController) {
        guard let rootViewController = getRootViewController() else {
            print("❌ IOSGalleryPicker: No root view controller available")
            self.onCancelled?()
            return
        }

        rootViewController.present(picker, animated: true) {
            print("✅ IOSGalleryPicker: Picker presented")
        }
    }

    /**
     * Get root view controller
     */
    private func getRootViewController() -> UIViewController? {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            return nil
        }

        // If root is presenting another VC, use the presented one
        if let presented = rootViewController.presentedViewController {
            return presented
        }

        return rootViewController
    }

    /**
     * Copy image from PHPicker result to app cache directory
     * Returns file path
     */
    private func copyImageToCache(from itemProvider: NSItemProvider, completion: @escaping (String?) -> Void) {
        // Check if item provider has image
        guard itemProvider.canLoadObject(ofClass: UIImage.self) else {
            print("❌ IOSGalleryPicker: Item provider cannot load image")
            completion(nil)
            return
        }

        // Load image data
        itemProvider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, error in
            if let error = error {
                print("❌ IOSGalleryPicker: Failed to load image - \(error.localizedDescription)")
                completion(nil)
                return
            }

            guard let sourceURL = url else {
                print("❌ IOSGalleryPicker: No source URL")
                completion(nil)
                return
            }

            // Generate unique filename
            let timestamp = Date().timeIntervalSince1970
            let fileName = "gallery_image_\(Int(timestamp * 1000)).jpg"

            // Get cache directory
            let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            let destinationURL = cacheDir.appendingPathComponent(fileName)

            do {
                // Load image and save as JPEG
                guard let image = UIImage(contentsOfFile: sourceURL.path) else {
                    print("❌ IOSGalleryPicker: Failed to create UIImage from file")
                    completion(nil)
                    return
                }

                // Convert to JPEG data
                guard let jpegData = image.jpegData(compressionQuality: 0.9) else {
                    print("❌ IOSGalleryPicker: Failed to convert to JPEG")
                    completion(nil)
                    return
                }

                // Write to cache directory
                try jpegData.write(to: destinationURL)

                let filePath = destinationURL.path
                print("✅ IOSGalleryPicker: Image copied to \(filePath)")
                completion(filePath)

            } catch {
                print("❌ IOSGalleryPicker: Failed to copy image - \(error.localizedDescription)")
                completion(nil)
            }
        }
    }
}

// MARK: - PHPickerViewControllerDelegate

extension IOSGalleryPicker: PHPickerViewControllerDelegate {

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        print("📸 IOSGalleryPicker: Picker finished with \(results.count) result(s)")

        // Dismiss picker
        picker.dismiss(animated: true)

        // Check if user cancelled
        guard !results.isEmpty else {
            print("ℹ️ IOSGalleryPicker: User cancelled selection")
            self.onCancelled?()
            self.clearCallbacks()
            return
        }

        // Single image selection
        if let onImageSelected = self.onImageSelected {
            let result = results[0]
            self.copyImageToCache(from: result.itemProvider) { filePath in
                DispatchQueue.main.async {
                    if let filePath = filePath {
                        print("✅ IOSGalleryPicker: Single image selected - \(filePath)")
                        onImageSelected(filePath)
                    } else {
                        print("❌ IOSGalleryPicker: Failed to copy image")
                        self.onCancelled?()
                    }
                    self.clearCallbacks()
                }
            }
        }
        // Multiple image selection
        else if let onImagesSelected = self.onImagesSelected {
            var filePaths: [String] = []
            let group = DispatchGroup()

            for result in results {
                group.enter()
                self.copyImageToCache(from: result.itemProvider) { filePath in
                    if let filePath = filePath {
                        filePaths.append(filePath)
                    }
                    group.leave()
                }
            }

            group.notify(queue: .main) {
                print("✅ IOSGalleryPicker: \(filePaths.count) images selected")
                onImagesSelected(filePaths)
                self.clearCallbacks()
            }
        }
    }

    /**
     * Clear callbacks after completion
     */
    private func clearCallbacks() {
        self.onImageSelected = nil
        self.onImagesSelected = nil
        self.onCancelled = nil
    }
}
