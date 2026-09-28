import Foundation
import UIKit

/**
 * Image compression utility for iOS
 * Equivalent to Android's ImageCompressor
 *
 * Optimizes images for storage and upload while maintaining quality
 */
@objc public class IOSImageCompressor: NSObject {

    /**
     * Compress image for storage
     * Quality: 0.8 (80%), Max resolution: 1920x1920, Max size: 2MB
     * Good balance between file size and quality
     */
    @objc public static func compressForStorage(
        sourceURL: URL,
        outputURL: URL,
        completion: @escaping @Sendable (Bool, Error?) -> Void
    ) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                guard let image = UIImage(contentsOfFile: sourceURL.path) else {
                    throw NSError(domain: "IOSImageCompressor", code: -1, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to load image"
                    ])
                }

                // Resize to max 1920x1920
                let resizedImage = Self.resize(image: image, maxDimension: 1920)

                // Compress with quality 0.8 and max size 2MB
                guard let compressedData = Self.compress(
                    image: resizedImage,
                    quality: 0.8,
                    maxSizeBytes: 2_097_152
                ) else {
                    throw NSError(domain: "IOSImageCompressor", code: -2, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to compress image"
                    ])
                }

                // Write to output
                try compressedData.write(to: outputURL)

                print("✅ iOS: Image compressed for storage")
                if let originalSize = try? Data(contentsOf: sourceURL).count {
                    print("   Original size: \(originalSize) bytes")
                }
                print("   Compressed size: \(compressedData.count) bytes")

                DispatchQueue.main.async {
                    completion(true, nil)
                }
            } catch {
                print("❌ iOS: Failed to compress image for storage: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    completion(false, error)
                }
            }
        }
    }

    /**
     * Compress image for upload
     * More aggressive compression for bandwidth optimization
     * Quality: 0.75 (75%), Max resolution: 1920x1920, Max size: 1.5MB
     */
    @objc public static func compressForUpload(
        sourceURL: URL,
        outputURL: URL,
        completion: @escaping @Sendable (Bool, Error?) -> Void
    ) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                guard let image = UIImage(contentsOfFile: sourceURL.path) else {
                    throw NSError(domain: "IOSImageCompressor", code: -1, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to load image"
                    ])
                }

                // Resize to max 1920x1920
                let resizedImage = Self.resize(image: image, maxDimension: 1920)

                // Compress with quality 0.75 and max size 1.5MB
                guard let compressedData = Self.compress(
                    image: resizedImage,
                    quality: 0.75,
                    maxSizeBytes: 1_572_864
                ) else {
                    throw NSError(domain: "IOSImageCompressor", code: -2, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to compress image"
                    ])
                }

                // Write to output
                try compressedData.write(to: outputURL)

                print("✅ iOS: Image compressed for upload")
                print("   Compressed size: \(compressedData.count) bytes")

                DispatchQueue.main.async {
                    completion(true, nil)
                }
            } catch {
                print("❌ iOS: Failed to compress image for upload: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    completion(false, error)
                }
            }
        }
    }

    /**
     * Generate thumbnail
     * Quality: 0.75 (75%), Max resolution: 300x300, Max size: 100KB
     * Small file size for fast loading in lists
     */
    @objc public static func generateThumbnail(
        sourceURL: URL,
        outputURL: URL,
        completion: @escaping @Sendable (Bool, Error?) -> Void
    ) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                guard let image = UIImage(contentsOfFile: sourceURL.path) else {
                    throw NSError(domain: "IOSImageCompressor", code: -1, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to load image"
                    ])
                }

                // Resize to max 300x300 (thumbnail)
                let thumbnail = Self.resize(image: image, maxDimension: 300)

                // Compress with quality 0.75 and max size 100KB
                guard let compressedData = Self.compress(
                    image: thumbnail,
                    quality: 0.75,
                    maxSizeBytes: 102_400
                ) else {
                    throw NSError(domain: "IOSImageCompressor", code: -2, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to compress thumbnail"
                    ])
                }

                // Write to output
                try compressedData.write(to: outputURL)

                print("✅ iOS: Thumbnail generated")
                print("   Size: \(compressedData.count) bytes")

                DispatchQueue.main.async {
                    completion(true, nil)
                }
            } catch {
                print("❌ iOS: Failed to generate thumbnail: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    completion(false, error)
                }
            }
        }
    }

    // MARK: - Private Helper Methods

    /**
     * Resize image to fit within maxDimension while maintaining aspect ratio
     */
    private static func resize(image: UIImage, maxDimension: CGFloat) -> UIImage {
        let size = image.size
        let widthRatio = maxDimension / size.width
        let heightRatio = maxDimension / size.height
        let ratio = min(widthRatio, heightRatio, 1.0) // Don't upscale

        let newSize = CGSize(
            width: size.width * ratio,
            height: size.height * ratio
        )

        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    /**
     * Compress image with quality and max size constraints
     * Uses iterative compression to meet size target
     */
    private static func compress(
        image: UIImage,
        quality: CGFloat,
        maxSizeBytes: Int
    ) -> Data? {
        var compression = quality
        var data = image.jpegData(compressionQuality: compression)

        // Iteratively reduce quality if size exceeds max
        while let imageData = data, imageData.count > maxSizeBytes && compression > 0.1 {
            compression -= 0.1
            data = image.jpegData(compressionQuality: compression)
        }

        return data
    }

    /**
     * Get image dimensions without loading full image into memory
     */
    @objc public static func getImageDimensions(url: URL) -> CGSize {
        guard let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? CGFloat,
              let height = properties[kCGImagePropertyPixelHeight] as? CGFloat else {
            return .zero
        }

        return CGSize(width: width, height: height)
    }

    /**
     * Get file size in bytes
     */
    @objc public static func getFileSize(url: URL) -> Int64 {
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
            return attributes[.size] as? Int64 ?? 0
        } catch {
            return 0
        }
    }
}
