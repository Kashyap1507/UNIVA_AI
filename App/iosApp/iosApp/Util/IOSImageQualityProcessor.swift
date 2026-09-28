import Foundation
import UIKit
import shared

/**
 * iOS Image Quality Processor for Document Scanning
 *
 * Applies resolution and compression presets to captured document images
 * based on user's scan quality setting.
 *
 * Features:
 * - Resize to preset dimensions (SMALL/STANDARD/LARGE/HD)
 * - Compress to target file size
 * - Preserve aspect ratio
 * - Maintain image orientation
 * - Memory-efficient processing for large images
 *
 * Usage:
 * ```swift
 * let processor = IOSImageQualityProcessor.shared
 * processor.processImage(
 *     image: capturedImage,
 *     quality: ScanQuality.large
 * ) { result in
 *     switch result {
 *     case .success(let processedImage):
 *         // Use processed image
 *     case .failure(let error):
 *         // Handle error
 *     }
 * }
 * ```
 */
@MainActor
class IOSImageQualityProcessor {

    // MARK: - Singleton

    static let shared = IOSImageQualityProcessor()

    // MARK: - Private Init

    private init() {}

    // MARK: - Constants

    /**
     * JPEG quality settings for different presets
     * Higher quality = larger file size, better image
     */
    private enum JPEGQuality {
        static let small: CGFloat = 0.75      // ~0.5 MB target
        static let standard: CGFloat = 0.80   // ~1.5 MB target
        static let large: CGFloat = 0.85      // ~3.0 MB target
        static let hd: CGFloat = 0.90         // ~5.0 MB target
    }

    // MARK: - Public Methods

    /**
     * Process captured image with quality preset
     *
     * Resizes and compresses the image according to the quality preset.
     *
     * @param image Input UIImage (raw camera output or picked image)
     * @param quality Scan quality preset (SMALL, STANDARD, LARGE, HD)
     * @return Processed UIImage or nil on failure
     */
    func processImage(image: UIImage, quality: ScanQuality) -> UIImage? {
        // Resize to quality preset dimensions
        guard let resizedImage = resize(image: image, quality: quality) else {
            print("❌ ImageQualityProcessor: Failed to resize image")
            return nil
        }

        print("✅ ImageQualityProcessor: Processed image with \(quality.displayName) quality")
        print("   Original size: \(Int(image.size.width))x\(Int(image.size.height))")
        print("   Processed size: \(Int(resizedImage.size.width))x\(Int(resizedImage.size.height))")

        return resizedImage
    }

    /**
     * Process and save image to file path
     *
     * @param image Input UIImage
     * @param quality Scan quality preset
     * @param outputPath File path to save processed image
     * @return True if successful, false otherwise
     */
    func processAndSave(image: UIImage, quality: ScanQuality, outputPath: String) -> Bool {
        guard let processedImage = processImage(image: image, quality: quality) else {
            return false
        }

        return save(image: processedImage, path: outputPath, quality: quality)
    }

    /**
     * Get JPEG data with quality-specific compression
     *
     * @param image UIImage to compress
     * @param quality Scan quality preset
     * @return JPEG data or nil on failure
     */
    func getJPEGData(image: UIImage, quality: ScanQuality) -> Data? {
        let compressionQuality = jpegQuality(for: quality)
        return image.jpegData(compressionQuality: compressionQuality)
    }

    /**
     * Get estimated file size for quality preset
     *
     * @param quality Scan quality preset
     * @return Estimated file size in MB
     */
    func getEstimatedFileSize(quality: ScanQuality) -> Double {
        return quality.estimatedSizeMB
    }

    /**
     * Check if processing is needed
     *
     * If image is already at or below target quality dimensions,
     * processing may be skipped.
     *
     * @param image UIImage to check
     * @param quality Target quality preset
     * @return True if processing recommended, false if can skip
     */
    func shouldProcess(image: UIImage, quality: ScanQuality) -> Bool {
        let width = Int(image.size.width)
        let height = Int(image.size.height)

        // Process if image is larger than target quality
        return width > Int(quality.width) || height > Int(quality.height)
    }

    // MARK: - File-Based Processing (for Kotlin Bridge)

    /**
     * Process image from file path
     * Used by Kotlin bridge for ImageQualityProcessor
     *
     * @param inputPath Path to input image file
     * @param quality Scan quality preset
     * @param outputPath Optional output path (if nil, creates temp file)
     * @return Result with output path on success, error on failure
     */
    func processImageFile(
        inputPath: String,
        quality: ScanQuality,
        outputPath: String?
    ) -> Result<String, Error> {
        print("📸 IOSImageQualityProcessor: Processing file with quality: \(quality.displayName)")
        print("   Input: \(inputPath)")

        // Load image from file
        guard let image = UIImage(contentsOfFile: inputPath) else {
            let error = NSError(domain: "IOSImageQualityProcessor", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Failed to load image from path"
            ])
            return .failure(error)
        }

        // Determine output path
        let finalOutputPath: String
        if let outputPath = outputPath {
            finalOutputPath = outputPath
        } else {
            // Create temp file
            let tempDir = FileManager.default.temporaryDirectory
            let filename = "processed_\(UUID().uuidString).jpg"
            finalOutputPath = tempDir.appendingPathComponent(filename).path
        }

        // Process and save
        if processAndSave(image: image, quality: quality, outputPath: finalOutputPath) {
            print("   ✅ Image processed successfully")
            print("   Output: \(finalOutputPath)")
            return .success(finalOutputPath)
        } else {
            let error = NSError(domain: "IOSImageQualityProcessor", code: -2, userInfo: [
                NSLocalizedDescriptionKey: "Failed to process and save image"
            ])
            return .failure(error)
        }
    }

    /**
     * Check if image file should be processed
     *
     * @param imagePath Path to image file
     * @param quality Target quality preset
     * @return True if processing recommended, false if can skip
     */
    func shouldProcessFile(imagePath: String, quality: ScanQuality) -> Bool {
        guard let image = UIImage(contentsOfFile: imagePath) else {
            return false
        }
        return shouldProcess(image: image, quality: quality)
    }

    /**
     * Get image dimensions from file without loading full image
     *
     * @param imagePath Path to image file
     * @return (width, height) or (0, 0) on failure
     */
    func getImageDimensionsFromFile(imagePath: String) -> (Int, Int) {
        let url = URL(fileURLWithPath: imagePath)
        guard let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [CFString: Any] else {
            return (0, 0)
        }

        let width = properties[kCGImagePropertyPixelWidth] as? Int ?? 0
        let height = properties[kCGImagePropertyPixelHeight] as? Int ?? 0

        return (width, height)
    }

    // MARK: - Private Methods

    /**
     * Resize image to quality preset dimensions
     *
     * Maintains aspect ratio while fitting within preset bounds.
     * Uses UIGraphicsImageRenderer for memory efficiency.
     *
     * @param image Input image
     * @param quality Target quality preset
     * @return Resized image or nil on failure
     */
    private func resize(image: UIImage, quality: ScanQuality) -> UIImage? {
        let currentWidth = image.size.width
        let currentHeight = image.size.height
        let targetWidth = CGFloat(quality.width)
        let targetHeight = CGFloat(quality.height)

        // Calculate scale to fit within target dimensions while maintaining aspect ratio
        let widthScale = targetWidth / currentWidth
        let heightScale = targetHeight / currentHeight
        let scale = min(widthScale, heightScale)

        // If image is already smaller than target, don't upscale
        if scale >= 1.0 {
            print("ℹ️ ImageQualityProcessor: Image already smaller than target, skipping resize")
            return image
        }

        let newWidth = currentWidth * scale
        let newHeight = currentHeight * scale
        let newSize = CGSize(width: newWidth, height: newHeight)

        // Use UIGraphicsImageRenderer for better memory management
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resizedImage = renderer.image { context in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }

        return resizedImage
    }

    /**
     * Save image to file with quality-specific compression
     *
     * @param image Image to save
     * @param path Output file path
     * @param quality Scan quality preset
     * @return True if save successful, false otherwise
     */
    private func save(image: UIImage, path: String, quality: ScanQuality) -> Bool {
        guard let jpegData = getJPEGData(image: image, quality: quality) else {
            print("❌ ImageQualityProcessor: Failed to convert image to JPEG")
            return false
        }

        let fileURL = URL(fileURLWithPath: path)

        do {
            try jpegData.write(to: fileURL)
            let fileSizeKB = Double(jpegData.count) / 1024.0
            print("✅ ImageQualityProcessor: Saved image to \(path)")
            print("   File size: \(String(format: "%.2f", fileSizeKB)) KB")
            return true
        } catch {
            print("❌ ImageQualityProcessor: Failed to save image - \(error.localizedDescription)")
            return false
        }
    }

    /**
     * Get JPEG compression quality for preset
     *
     * @param quality Scan quality preset
     * @return Compression quality (0.0 to 1.0)
     */
    private func jpegQuality(for quality: ScanQuality) -> CGFloat {
        switch quality {
        case .small:
            return JPEGQuality.small
        case .standard:
            return JPEGQuality.standard
        case .large:
            return JPEGQuality.large
        case .hd:
            return JPEGQuality.hd
        default:
            return JPEGQuality.standard // Safe default
        }
    }
}
