import Foundation
import Vision
import UIKit
import CoreImage

/**
 * iOS Edge Detector using Vision framework
 * Matches Android OpenCV implementation for document scanning
 *
 * Features:
 * - Automatic document detection using VNDetectRectanglesRequest
 * - Perspective transform using Core Image
 * - Confidence scoring matching Android thresholds
 */
class IOSEdgeDetector {

    /**
     * Detect document edges automatically using Vision framework.
     *
     * @param imagePath Path to the image file
     * @return DocumentCorners if detected, nil if no document found (triggers manual mode)
     */
    func detectEdges(imagePath: String) -> DocumentCorners? {
        print("📐 IOSEdgeDetector: Detecting edges in image...")

        // Load image
        guard let image = UIImage(contentsOfFile: imagePath),
              let cgImage = image.cgImage else {
            print("❌ IOSEdgeDetector: Failed to load image from: \(imagePath)")
            return nil
        }

        let imageSize = CGSize(width: cgImage.width, height: cgImage.height)
        print("   Image size: \(Int(imageSize.width))x\(Int(imageSize.height))")

        // Create Vision request
        let request = VNDetectRectanglesRequest()
        request.minimumAspectRatio = 0.3  // Allow narrow documents
        request.maximumAspectRatio = 1.0  // Up to square
        request.minimumSize = 0.2         // Minimum 20% of image
        request.maximumObservations = 1   // Only the most prominent rectangle
        request.minimumConfidence = 0.5   // 50% confidence threshold

        // Perform request
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])

        do {
            try handler.perform([request])

            guard let results = request.results, !results.isEmpty else {
                print("⚠️ IOSEdgeDetector: No rectangles detected")
                return nil
            }

            // Get the most confident rectangle
            let observation = results[0]
            print("✅ IOSEdgeDetector: Rectangle detected with confidence: \(observation.confidence)")

            // Convert normalized coordinates to pixel coordinates
            let corners = convertToPixelCoordinates(
                observation: observation,
                imageSize: imageSize
            )

            // Calculate confidence level matching Android thresholds
            let imageArea = imageSize.width * imageSize.height
            let confidence = calculateConfidence(corners: corners, imageArea: imageArea)

            print("   Confidence level: \(confidence.rawValue)")
            print("   Corners:")
            print("     Top-left: (\(Int(corners.topLeft.x)), \(Int(corners.topLeft.y)))")
            print("     Top-right: (\(Int(corners.topRight.x)), \(Int(corners.topRight.y)))")
            print("     Bottom-right: (\(Int(corners.bottomRight.x)), \(Int(corners.bottomRight.y)))")
            print("     Bottom-left: (\(Int(corners.bottomLeft.x)), \(Int(corners.bottomLeft.y)))")

            // If confidence is too low, return nil to trigger manual mode
            if confidence == .failed || confidence == .low {
                print("⚠️ IOSEdgeDetector: Confidence too low, triggering manual mode")
                return nil
            }

            return corners

        } catch {
            print("❌ IOSEdgeDetector: Vision request failed: \(error.localizedDescription)")
            return nil
        }
    }

    /**
     * Apply perspective transform to crop and straighten the document.
     *
     * @param imagePath Path to original image
     * @param corners The four corners of the document
     * @param rotation Rotation angle (0, 90, 180, 270 degrees)
     * @return Path to the transformed/cropped image
     */
    func applyPerspectiveTransform(
        imagePath: String,
        corners: DocumentCorners,
        rotation: Int
    ) -> String? {
        print("🔄 IOSEdgeDetector: Applying perspective transform...")

        // Load image
        guard let image = UIImage(contentsOfFile: imagePath),
              let cgImage = image.cgImage else {
            print("❌ IOSEdgeDetector: Failed to load image")
            return nil
        }

        let ciImage = CIImage(cgImage: cgImage)
        let imageSize = CGSize(width: cgImage.width, height: cgImage.height)

        // Order corners (TL, TR, BR, BL)
        let orderedCorners = orderCorners(corners)

        // Calculate destination dimensions
        let destWidth = max(
            distance(orderedCorners.topLeft, orderedCorners.topRight),
            distance(orderedCorners.bottomLeft, orderedCorners.bottomRight)
        )

        let destHeight = max(
            distance(orderedCorners.topLeft, orderedCorners.bottomLeft),
            distance(orderedCorners.topRight, orderedCorners.bottomRight)
        )

        print("   Transform to: \(Int(destWidth))x\(Int(destHeight))")

        // Convert corners to normalized coordinates for Core Image
        let normalizedTL = normalizePoint(orderedCorners.topLeft, imageSize: imageSize)
        let normalizedTR = normalizePoint(orderedCorners.topRight, imageSize: imageSize)
        let normalizedBR = normalizePoint(orderedCorners.bottomRight, imageSize: imageSize)
        let normalizedBL = normalizePoint(orderedCorners.bottomLeft, imageSize: imageSize)

        // Apply perspective correction
        guard let filter = CIFilter(name: "CIPerspectiveCorrection") else {
            print("❌ IOSEdgeDetector: Failed to create perspective filter")
            return nil
        }

        filter.setValue(ciImage, forKey: kCIInputImageKey)
        filter.setValue(CIVector(cgPoint: normalizedTL), forKey: "inputTopLeft")
        filter.setValue(CIVector(cgPoint: normalizedTR), forKey: "inputTopRight")
        filter.setValue(CIVector(cgPoint: normalizedBR), forKey: "inputBottomRight")
        filter.setValue(CIVector(cgPoint: normalizedBL), forKey: "inputBottomLeft")

        guard let outputImage = filter.outputImage else {
            print("❌ IOSEdgeDetector: Perspective transform failed")
            return nil
        }

        // Apply rotation if needed
        let rotatedImage = applyRotation(outputImage, rotation: rotation)

        // Render to UIImage
        let context = CIContext()
        guard let cgOutput = context.createCGImage(rotatedImage, from: rotatedImage.extent) else {
            print("❌ IOSEdgeDetector: Failed to render output image")
            return nil
        }

        let transformedImage = UIImage(cgImage: cgOutput)

        // Save to file
        let outputPath = generateOutputPath(inputPath: imagePath)
        guard let jpegData = transformedImage.jpegData(compressionQuality: 0.85) else {
            print("❌ IOSEdgeDetector: Failed to convert to JPEG")
            return nil
        }

        do {
            try jpegData.write(to: URL(fileURLWithPath: outputPath))
            print("✅ IOSEdgeDetector: Saved transformed image to: \(outputPath)")
            print("   File size: \(jpegData.count / 1024) KB")
            return outputPath
        } catch {
            print("❌ IOSEdgeDetector: Failed to save image: \(error.localizedDescription)")
            return nil
        }
    }

    /**
     * Get default corners for manual mode (small margin).
     */
    func getDefaultCorners(imageWidth: Int, imageHeight: Int) -> DocumentCorners {
        let margin: CGFloat = 20
        return DocumentCorners(
            topLeft: CGPoint(x: margin, y: margin),
            topRight: CGPoint(x: CGFloat(imageWidth) - margin, y: margin),
            bottomRight: CGPoint(x: CGFloat(imageWidth) - margin, y: CGFloat(imageHeight) - margin),
            bottomLeft: CGPoint(x: margin, y: CGFloat(imageHeight) - margin),
            confidence: .failed
        )
    }

    /**
     * Get image dimensions without loading the full image.
     */
    func getImageSize(imagePath: String) -> CGSize {
        guard let imageSource = CGImageSourceCreateWithURL(URL(fileURLWithPath: imagePath) as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [String: Any],
              let width = properties[kCGImagePropertyPixelWidth as String] as? Int,
              let height = properties[kCGImagePropertyPixelHeight as String] as? Int else {
            print("⚠️ IOSEdgeDetector: Failed to get image size, using default")
            return CGSize(width: 1080, height: 1440)
        }

        return CGSize(width: width, height: height)
    }

    // MARK: - Private Helper Methods

    /**
     * Convert Vision normalized coordinates to pixel coordinates.
     * Vision uses bottom-left origin, we need top-left origin.
     */
    private func convertToPixelCoordinates(
        observation: VNRectangleObservation,
        imageSize: CGSize
    ) -> DocumentCorners {
        // Vision coordinates are normalized (0-1) with origin at bottom-left
        // We need pixel coordinates with origin at top-left

        let topLeft = CGPoint(
            x: observation.topLeft.x * imageSize.width,
            y: (1 - observation.topLeft.y) * imageSize.height
        )

        let topRight = CGPoint(
            x: observation.topRight.x * imageSize.width,
            y: (1 - observation.topRight.y) * imageSize.height
        )

        let bottomRight = CGPoint(
            x: observation.bottomRight.x * imageSize.width,
            y: (1 - observation.bottomRight.y) * imageSize.height
        )

        let bottomLeft = CGPoint(
            x: observation.bottomLeft.x * imageSize.width,
            y: (1 - observation.bottomLeft.y) * imageSize.height
        )

        return DocumentCorners(
            topLeft: topLeft,
            topRight: topRight,
            bottomRight: bottomRight,
            bottomLeft: bottomLeft,
            confidence: .high  // Will be recalculated
        )
    }

    /**
     * Normalize point for Core Image (bottom-left origin).
     */
    private func normalizePoint(_ point: CGPoint, imageSize: CGSize) -> CGPoint {
        return CGPoint(
            x: point.x,
            y: imageSize.height - point.y  // Flip Y for Core Image
        )
    }

    /**
     * Calculate confidence level matching Android thresholds.
     * HIGH: >70%, MEDIUM: 40-70%, LOW: <40%
     */
    private func calculateConfidence(corners: DocumentCorners, imageArea: CGFloat) -> DetectionConfidence {
        let quadArea = calculatePolygonArea(corners: corners)
        let ratio = quadArea / imageArea

        print("   Quad area ratio: \(String(format: "%.2f", ratio * 100))%")

        if ratio > 0.7 {
            return .high
        } else if ratio > 0.4 {
            return .medium
        } else if ratio > 0.05 {
            return .low
        } else {
            return .failed
        }
    }

    /**
     * Calculate area of quadrilateral using Shoelace formula.
     */
    private func calculatePolygonArea(corners: DocumentCorners) -> CGFloat {
        let points = [corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft]
        var area: CGFloat = 0

        for i in 0..<points.count {
            let j = (i + 1) % points.count
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }

        return abs(area) / 2.0
    }

    /**
     * Order corners properly (TL, TR, BR, BL).
     */
    private func orderCorners(_ corners: DocumentCorners) -> DocumentCorners {
        let points = [corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft]

        // Find corners by quadrant
        let topLeft = points.min(by: { $0.x + $0.y < $1.x + $1.y })!
        let bottomRight = points.max(by: { $0.x + $0.y < $1.x + $1.y })!

        let remaining = points.filter { $0 != topLeft && $0 != bottomRight }
        let topRight = remaining.min(by: { $0.y - $0.x < $1.y - $1.x })!
        let bottomLeft = remaining.first { $0 != topRight }!

        return DocumentCorners(
            topLeft: topLeft,
            topRight: topRight,
            bottomRight: bottomRight,
            bottomLeft: bottomLeft,
            confidence: corners.confidence
        )
    }

    /**
     * Calculate distance between two points.
     */
    private func distance(_ p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        let dx = p2.x - p1.x
        let dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Apply rotation to image.
     */
    private func applyRotation(_ image: CIImage, rotation: Int) -> CIImage {
        switch rotation {
        case 90:
            return image.oriented(.right)
        case 180:
            return image.oriented(.down)
        case 270:
            return image.oriented(.left)
        default:
            return image
        }
    }

    /**
     * Generate output path for transformed image.
     */
    private func generateOutputPath(inputPath: String) -> String {
        let url = URL(fileURLWithPath: inputPath)
        let filename = url.deletingPathExtension().lastPathComponent
        let directory = url.deletingLastPathComponent()
        let timestamp = Int(Date().timeIntervalSince1970)

        return directory
            .appendingPathComponent("\(filename)_cropped_\(timestamp).jpg")
            .path
    }
}

// MARK: - Data Models

/**
 * Document corners with confidence level.
 */
struct DocumentCorners {
    let topLeft: CGPoint
    let topRight: CGPoint
    let bottomRight: CGPoint
    let bottomLeft: CGPoint
    let confidence: DetectionConfidence
}

/**
 * Detection confidence levels matching Android thresholds.
 */
enum DetectionConfidence: String {
    case high = "HIGH"      // >70% of image
    case medium = "MEDIUM"  // 40-70% of image
    case low = "LOW"        // <40% of image
    case failed = "FAILED"  // Detection failed
}
