import Foundation
import UIKit
import shared

/// Swift bridge to connect IOSEdgeDetector to Kotlin.
/// Provides callback-based API for Kotlin to use Vision framework edge detection.
/// Note: @MainActor removed to allow DispatchQueue.async access to properties.
class IOSEdgeDetectorBridge {

    // Use nonisolated(unsafe) to allow safe access from background threads
    // IOSEdgeDetector operations are thread-safe for Vision framework operations
    private nonisolated(unsafe) let edgeDetector = IOSEdgeDetector()

    /**
     * Detect edges in an image.
     * Returns corners via callback if detection successful, nil if failed (triggers manual mode).
     */
    nonisolated func detectEdges(
        imagePath: String,
        onSuccess: @escaping @Sendable (EdgeDetectionResult) -> Void,
        onFailure: @escaping @Sendable () -> Void
    ) {
        print("🌉 IOSEdgeDetectorBridge: detectEdges called for: \(imagePath)")

        let detector = self.edgeDetector
        // Run detection on background thread
        DispatchQueue.global(qos: .userInitiated).async {
            if let corners = detector.detectEdges(imagePath: imagePath) {
                let result = EdgeDetectionResult(
                    topLeftX: Float(corners.topLeft.x),
                    topLeftY: Float(corners.topLeft.y),
                    topRightX: Float(corners.topRight.x),
                    topRightY: Float(corners.topRight.y),
                    bottomRightX: Float(corners.bottomRight.x),
                    bottomRightY: Float(corners.bottomRight.y),
                    bottomLeftX: Float(corners.bottomLeft.x),
                    bottomLeftY: Float(corners.bottomLeft.y),
                    confidence: corners.confidence.rawValue
                )

                DispatchQueue.main.async {
                    onSuccess(result)
                }
            } else {
                DispatchQueue.main.async {
                    onFailure()
                }
            }
        }
    }

    /**
     * Apply perspective transform to crop and straighten document.
     */
    nonisolated func applyPerspectiveTransform(
        imagePath: String,
        topLeft: CGPoint,
        topRight: CGPoint,
        bottomRight: CGPoint,
        bottomLeft: CGPoint,
        rotation: Int,
        onSuccess: @escaping @Sendable (String) -> Void,
        onError: @escaping @Sendable (String) -> Void
    ) {
        print("🌉 IOSEdgeDetectorBridge: applyPerspectiveTransform called")

        let detector = self.edgeDetector
        // Run transform on background thread
        DispatchQueue.global(qos: .userInitiated).async {
            let corners = DocumentCorners(
                topLeft: topLeft,
                topRight: topRight,
                bottomRight: bottomRight,
                bottomLeft: bottomLeft,
                confidence: .high
            )

            if let outputPath = detector.applyPerspectiveTransform(
                imagePath: imagePath,
                corners: corners,
                rotation: rotation
            ) {
                DispatchQueue.main.async {
                    onSuccess(outputPath)
                }
            } else {
                DispatchQueue.main.async {
                    onError("Failed to apply perspective transform")
                }
            }
        }
    }

    /**
     * Get default corners for manual mode.
     */
    func getDefaultCorners(imageWidth: Int, imageHeight: Int) -> EdgeDetectionResult {
        let corners = edgeDetector.getDefaultCorners(imageWidth: imageWidth, imageHeight: imageHeight)

        return EdgeDetectionResult(
            topLeftX: Float(corners.topLeft.x),
            topLeftY: Float(corners.topLeft.y),
            topRightX: Float(corners.topRight.x),
            topRightY: Float(corners.topRight.y),
            bottomRightX: Float(corners.bottomRight.x),
            bottomRightY: Float(corners.bottomRight.y),
            bottomLeftX: Float(corners.bottomLeft.x),
            bottomLeftY: Float(corners.bottomLeft.y),
            confidence: corners.confidence.rawValue
        )
    }

    /**
     * Get image dimensions.
     */
    func getImageSize(imagePath: String) -> ImageDimensions {
        let size = edgeDetector.getImageSize(imagePath: imagePath)
        return ImageDimensions(
            width: Int(size.width),
            height: Int(size.height)
        )
    }
}

// MARK: - Data Models for Kotlin Bridge

/**
 * Edge detection result for Kotlin consumption.
 */
@objc public class EdgeDetectionResult: NSObject {
    @objc public let topLeftX: Float
    @objc public let topLeftY: Float
    @objc public let topRightX: Float
    @objc public let topRightY: Float
    @objc public let bottomRightX: Float
    @objc public let bottomRightY: Float
    @objc public let bottomLeftX: Float
    @objc public let bottomLeftY: Float
    @objc public let confidence: String

    init(
        topLeftX: Float,
        topLeftY: Float,
        topRightX: Float,
        topRightY: Float,
        bottomRightX: Float,
        bottomRightY: Float,
        bottomLeftX: Float,
        bottomLeftY: Float,
        confidence: String
    ) {
        self.topLeftX = topLeftX
        self.topLeftY = topLeftY
        self.topRightX = topRightX
        self.topRightY = topRightY
        self.bottomRightX = bottomRightX
        self.bottomRightY = bottomRightY
        self.bottomLeftX = bottomLeftX
        self.bottomLeftY = bottomLeftY
        self.confidence = confidence
    }
}

/**
 * Image dimensions for Kotlin consumption.
 */
@objc public class ImageDimensions: NSObject {
    @objc public let width: Int
    @objc public let height: Int

    init(width: Int, height: Int) {
        self.width = width
        self.height = height
    }
}

// MARK: - Global Bridge Instance

// Use nonisolated(unsafe) to satisfy Swift 6 concurrency requirements
// The bridge is stateless and thread-safe for Vision framework operations
private nonisolated(unsafe) let edgeDetectorBridgeInstance = IOSEdgeDetectorBridge()

/**
 * Kotlin-accessible functions via IOSCallbackBridge.
 */
@objc public class IOSEdgeDetectorCallbacks: NSObject {

    @objc public static func detectEdges(
        imagePath: String,
        onSuccess: @escaping @Sendable (EdgeDetectionResult) -> Void,
        onFailure: @escaping @Sendable () -> Void
    ) {
        edgeDetectorBridgeInstance.detectEdges(
            imagePath: imagePath,
            onSuccess: onSuccess,
            onFailure: onFailure
        )
    }

    @objc public static func applyPerspectiveTransform(
        imagePath: String,
        topLeftX: CGFloat,
        topLeftY: CGFloat,
        topRightX: CGFloat,
        topRightY: CGFloat,
        bottomRightX: CGFloat,
        bottomRightY: CGFloat,
        bottomLeftX: CGFloat,
        bottomLeftY: CGFloat,
        rotation: Int,
        onSuccess: @escaping @Sendable (String) -> Void,
        onError: @escaping @Sendable (String) -> Void
    ) {
        edgeDetectorBridgeInstance.applyPerspectiveTransform(
            imagePath: imagePath,
            topLeft: CGPoint(x: topLeftX, y: topLeftY),
            topRight: CGPoint(x: topRightX, y: topRightY),
            bottomRight: CGPoint(x: bottomRightX, y: bottomRightY),
            bottomLeft: CGPoint(x: bottomLeftX, y: bottomLeftY),
            rotation: rotation,
            onSuccess: onSuccess,
            onError: onError
        )
    }

    @objc public static func getDefaultCorners(imageWidth: Int, imageHeight: Int) -> EdgeDetectionResult {
        return edgeDetectorBridgeInstance.getDefaultCorners(
            imageWidth: imageWidth,
            imageHeight: imageHeight
        )
    }

    @objc public static func getImageSize(imagePath: String) -> ImageDimensions {
        return edgeDetectorBridgeInstance.getImageSize(imagePath: imagePath)
    }
}
