import Foundation
import UIKit
import shared

/**
 * File storage manager for iOS
 * Handles document images and thumbnails using iOS filesystem
 */
@MainActor
class FileStorageManager {

    // MARK: - Singleton

    static let shared = FileStorageManager()

    // MARK: - Properties

    private let fileManager = FileManager.default
    private let documentsDirectory: URL
    private let documentsImagesDirectory: URL
    private let thumbnailsDirectory: URL
    private let qualityProcessor = IOSImageQualityProcessor.shared

    // MARK: - Initialization

    private init() {
        // Get documents directory
        documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]

        // Create subdirectories
        documentsImagesDirectory = documentsDirectory.appendingPathComponent("documents/images")
        thumbnailsDirectory = documentsDirectory.appendingPathComponent("documents/thumbnails")

        // Create directories if they don't exist
        createDirectories()
    }

    private func createDirectories() {
        let directories = [documentsImagesDirectory, thumbnailsDirectory]

        for directory in directories {
            do {
                try fileManager.createDirectory(at: directory, withIntermediateDirectories: true, attributes: nil)
                print("✅ FileStorage: Created directory at \(directory.path)")
            } catch {
                print("❌ FileStorage: Error creating directory: \(error)")
            }
        }
    }

    // MARK: - Save Image

    /**
     * Save image with default compression quality (0.8)
     * Legacy method for backward compatibility
     */
    func saveImage(_ image: UIImage, documentId: String, pageNumber: Int) -> String? {
        let filename = "\(documentId)_page_\(pageNumber).jpg"
        let fileURL = documentsImagesDirectory.appendingPathComponent(filename)

        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("❌ FileStorage: Failed to convert image to JPEG data")
            return nil
        }

        do {
            try imageData.write(to: fileURL)
            print("✅ FileStorage: Saved image to \(fileURL.path)")
            return fileURL.path
        } catch {
            print("❌ FileStorage: Error saving image: \(error)")
            return nil
        }
    }

    /**
     * Save image with quality preset
     * Uses IOSImageQualityProcessor to apply resolution and compression settings
     *
     * @param image Original captured image
     * @param documentId Document ID
     * @param pageNumber Page number
     * @param quality Scan quality preset (SMALL/STANDARD/LARGE/HD)
     * @return File path if successful, nil otherwise
     */
    func saveImage(_ image: UIImage, documentId: String, pageNumber: Int, quality: ScanQuality) -> String? {
        let filename = "\(documentId)_page_\(pageNumber).jpg"
        let fileURL = documentsImagesDirectory.appendingPathComponent(filename)

        // Process image with quality preset
        guard let processedImage = qualityProcessor.processImage(image: image, quality: quality) else {
            print("❌ FileStorage: Failed to process image with quality preset")
            return nil
        }

        // Get JPEG data with quality-specific compression
        guard let imageData = qualityProcessor.getJPEGData(image: processedImage, quality: quality) else {
            print("❌ FileStorage: Failed to convert processed image to JPEG data")
            return nil
        }

        do {
            try imageData.write(to: fileURL)
            let fileSizeKB = Double(imageData.count) / 1024.0
            print("✅ FileStorage: Saved image with \(quality.displayName) quality to \(fileURL.path)")
            print("   File size: \(String(format: "%.2f", fileSizeKB)) KB")
            return fileURL.path
        } catch {
            print("❌ FileStorage: Error saving image: \(error)")
            return nil
        }
    }

    // MARK: - Save Thumbnail

    func saveThumbnail(_ image: UIImage, documentId: String, pageNumber: Int) -> String? {
        let filename = "\(documentId)_page_\(pageNumber)_thumb.jpg"
        let fileURL = thumbnailsDirectory.appendingPathComponent(filename)

        // Resize image to thumbnail size (300x300)
        let thumbnailImage = resizeImage(image, targetSize: CGSize(width: 300, height: 300))

        guard let imageData = thumbnailImage.jpegData(compressionQuality: 0.7) else {
            print("❌ FileStorage: Failed to convert thumbnail to JPEG data")
            return nil
        }

        do {
            try imageData.write(to: fileURL)
            print("✅ FileStorage: Saved thumbnail to \(fileURL.path)")
            return fileURL.path
        } catch {
            print("❌ FileStorage: Error saving thumbnail: \(error)")
            return nil
        }
    }

    // MARK: - Get Image

    func getImage(atPath path: String) -> UIImage? {
        let fileURL = URL(fileURLWithPath: path)

        guard fileManager.fileExists(atPath: fileURL.path) else {
            print("⚠️ FileStorage: File does not exist at \(path)")
            return nil
        }

        return UIImage(contentsOfFile: fileURL.path)
    }

    // MARK: - Delete Image

    func deleteImage(atPath path: String) -> Bool {
        let fileURL = URL(fileURLWithPath: path)

        do {
            try fileManager.removeItem(at: fileURL)
            print("✅ FileStorage: Deleted file at \(path)")
            return true
        } catch {
            print("❌ FileStorage: Error deleting file: \(error)")
            return false
        }
    }

    // MARK: - Get Storage Used

    func getStorageUsed() -> Int64 {
        var totalSize: Int64 = 0

        let directories = [documentsImagesDirectory, thumbnailsDirectory]

        for directory in directories {
            if let enumerator = fileManager.enumerator(at: directory, includingPropertiesForKeys: [.fileSizeKey]) {
                for case let fileURL as URL in enumerator {
                    do {
                        let resourceValues = try fileURL.resourceValues(forKeys: [.fileSizeKey])
                        totalSize += Int64(resourceValues.fileSize ?? 0)
                    } catch {
                        print("⚠️ FileStorage: Error getting file size: \(error)")
                    }
                }
            }
        }

        return totalSize
    }

    // MARK: - Clear All

    func clearAll() throws {
        let directories = [documentsImagesDirectory, thumbnailsDirectory]

        for directory in directories {
            if fileManager.fileExists(atPath: directory.path) {
                try fileManager.removeItem(at: directory)
            }
        }

        // Recreate directories
        createDirectories()
        print("🗑️ FileStorage: Cleared all document files")
    }

    // MARK: - Helper Methods

    private func resizeImage(_ image: UIImage, targetSize: CGSize) -> UIImage {
        let size = image.size

        let widthRatio  = targetSize.width  / size.width
        let heightRatio = targetSize.height / size.height

        // Determine what the orientation of the image is and use that to get the new size
        var newSize: CGSize
        if(widthRatio > heightRatio) {
            newSize = CGSize(width: size.width * heightRatio, height: size.height * heightRatio)
        } else {
            newSize = CGSize(width: size.width * widthRatio,  height: size.height * widthRatio)
        }

        // This is the rect that we've calculated out and this is what is actually used below
        let rect = CGRect(x: 0, y: 0, width: newSize.width, height: newSize.height)

        // Actually do the resizing to the rect using the ImageContext stuff
        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        image.draw(in: rect)
        let newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return newImage ?? image
    }
}
