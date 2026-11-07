package com.kashyap.wizlyai.vision

import com.kashyap.wizlyai.ios.IOSCallbackBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS implementation of EdgeDetector using Vision framework.
 *
 * Bridges to Swift IOSEdgeDetector which uses:
 * - VNDetectRectanglesRequest for automatic document detection
 * - CIPerspectiveCorrection for perspective transform
 */
actual class EdgeDetector {
    /**
     * Detect document edges automatically using Vision framework.
     *
     * @return DocumentEdges if detection successful, null if no document found (triggers manual mode)
     */
    actual suspend fun detectEdges(imagePath: String): DocumentEdges? {
        println("📐 iOS EdgeDetector: Detecting edges for: $imagePath")

        val callbacks = IOSCallbackBridge.getEdgeDetectorCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS EdgeDetector: Callbacks not set, returning null (manual mode)")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            callbacks.detectEdges(
                imagePath,
                { tlX, tlY, trX, trY, brX, brY, blX, blY, confidence ->
                    println("✅ iOS EdgeDetector: Detection successful with confidence: $confidence")

                    val confidenceLevel =
                        when (confidence) {
                            "HIGH" -> DetectionConfidence.HIGH
                            "MEDIUM" -> DetectionConfidence.MEDIUM
                            "LOW" -> DetectionConfidence.LOW
                            else -> DetectionConfidence.FAILED
                        }

                    val edges =
                        DocumentEdges(
                            topLeft = Point(tlX, tlY),
                            topRight = Point(trX, trY),
                            bottomRight = Point(brX, brY),
                            bottomLeft = Point(blX, blY),
                            confidence = confidenceLevel,
                            mode = DetectionMode.AUTO,
                        )

                    continuation.resume(edges)
                },
                {
                    println("⚠️ iOS EdgeDetector: Detection failed, triggering manual mode")
                    continuation.resume(null)
                },
            )
        }
    }

    /**
     * Apply perspective transform to crop and straighten the document.
     */
    actual suspend fun applyPerspectiveTransform(
        imagePath: String,
        corners: DocumentEdges?,
        rotation: Int,
    ): String {
        println("🔄 iOS EdgeDetector: Applying perspective transform")

        // If corners is null, return original image path (no transformation)
        if (corners == null) {
            println("⚠️ iOS EdgeDetector: No corners provided, returning original image")
            return imagePath
        }

        val callbacks = IOSCallbackBridge.getEdgeDetectorCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS EdgeDetector: Callbacks not set, returning original path")
            return imagePath
        }

        return suspendCancellableCoroutine { continuation ->
            callbacks.applyPerspectiveTransform(
                imagePath,
                corners.topLeft.x.toDouble(),
                corners.topLeft.y.toDouble(),
                corners.topRight.x.toDouble(),
                corners.topRight.y.toDouble(),
                corners.bottomRight.x.toDouble(),
                corners.bottomRight.y.toDouble(),
                corners.bottomLeft.x.toDouble(),
                corners.bottomLeft.y.toDouble(),
                rotation,
                { outputPath ->
                    println("✅ iOS EdgeDetector: Transform complete: $outputPath")
                    continuation.resume(outputPath)
                },
                { error ->
                    println("❌ iOS EdgeDetector: Transform failed: $error")
                    continuation.resumeWithException(Exception(error))
                },
            )
        }
    }

    /**
     * Get default corners for manual mode.
     */
    actual fun getDefaultCorners(
        imageWidth: Int,
        imageHeight: Int,
    ): DocumentEdges {
        println("📐 iOS EdgeDetector: Getting default corners for ${imageWidth}x$imageHeight")

        val callbacks = IOSCallbackBridge.getEdgeDetectorCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS EdgeDetector: Callbacks not set, using fallback")
            val margin = 20f
            return DocumentEdges(
                topLeft = Point(margin, margin),
                topRight = Point(imageWidth - margin, margin),
                bottomRight = Point(imageWidth - margin, imageHeight - margin),
                bottomLeft = Point(margin, imageHeight - margin),
                confidence = DetectionConfidence.FAILED,
                mode = DetectionMode.MANUAL,
            )
        }

        var result: DocumentEdges? = null
        callbacks.getDefaultCorners(
            imageWidth,
            imageHeight,
        ) { tlX, tlY, trX, trY, brX, brY, blX, blY ->
            result =
                DocumentEdges(
                    topLeft = Point(tlX, tlY),
                    topRight = Point(trX, trY),
                    bottomRight = Point(brX, brY),
                    bottomLeft = Point(blX, blY),
                    confidence = DetectionConfidence.FAILED,
                    mode = DetectionMode.MANUAL,
                )
        }

        return result ?: DocumentEdges(
            topLeft = Point(20f, 20f),
            topRight = Point(imageWidth - 20f, 20f),
            bottomRight = Point(imageWidth - 20f, imageHeight - 20f),
            bottomLeft = Point(20f, imageHeight - 20f),
            confidence = DetectionConfidence.FAILED,
            mode = DetectionMode.MANUAL,
        )
    }

    /**
     * Get image dimensions from file.
     */
    actual fun getImageSize(imagePath: String): ImageSize {
        println("📐 iOS EdgeDetector: Getting image size for: $imagePath")

        val callbacks = IOSCallbackBridge.getEdgeDetectorCallbacks()
        if (callbacks == null) {
            println("⚠️ iOS EdgeDetector: Callbacks not set, using default size")
            return ImageSize(width = 1080, height = 1440)
        }

        var size: ImageSize? = null
        callbacks.getImageSize(imagePath) { width, height ->
            size = ImageSize(width = width, height = height)
            println("   Size: ${width}x$height")
        }

        return size ?: ImageSize(width = 1080, height = 1440)
    }
}
