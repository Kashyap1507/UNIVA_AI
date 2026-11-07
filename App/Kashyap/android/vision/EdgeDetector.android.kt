package com.kashyap.wizlyai.vision

import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Android implementation of EdgeDetector using OpenCV.
 *
 * Provides automatic document edge detection using:
 * - Canny edge detection
 * - Contour approximation
 * - Quadrilateral detection
 * - Perspective transform
 */
actual class EdgeDetector {
    init {
        // Load OpenCV native library
        try {
            System.loadLibrary("opencv_java4")
            Log.d(TAG, "OpenCV loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV load failed", e)
            // Try alternative loading method
            if (!OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV initialization failed")
            }
        }
    }

    /**
     * Detect document edges automatically using OpenCV.
     *
     * Algorithm:
     * 1. Preprocess image (resize, grayscale, blur)
     * 2. Canny edge detection
     * 3. Find contours
     * 4. Identify largest quadrilateral
     * 5. Calculate confidence score (HIGH if >70%, MEDIUM if 40-70%, LOW if <40%)
     */
    actual suspend fun detectEdges(imagePath: String): DocumentEdges? =
        withContext(Dispatchers.Default) {
            var image: Mat? = null
            var resized: Mat? = null
            var gray: Mat? = null
            var edges: Mat? = null
            var hierarchy: Mat? = null
            val contours = mutableListOf<MatOfPoint>()

            try {
                // 1. Load image
                image = Imgcodecs.imread(imagePath)
                if (image == null || image.empty()) {
                    Log.e(TAG, "Failed to load image: $imagePath")
                    return@withContext null
                }

                // OpenCV uses cols() for width, rows() for height (width x height format)
                val imageWidth = image.cols()
                val imageHeight = image.rows()
                Log.d(TAG, "Loaded image: ${imageWidth}x$imageHeight (WxH)")

                // 2. Preprocess
                resized = preprocessImage(image)
                val scale = image.size() to resized.size()

                gray = Mat()
                Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY)
                Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

                // 3. Edge detection
                edges = Mat()
                Imgproc.Canny(gray, edges, 50.0, 200.0)
                Imgproc.dilate(edges, edges, Mat(), org.opencv.core.Point(-1.0, -1.0), 2)

                // 4. Find contours
                hierarchy = Mat()
                Imgproc.findContours(
                    edges,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE,
                )

                Log.d(TAG, "Found ${contours.size} contours")

                // 5. Find largest quadrilateral (on resized image)
                val resizedArea = resized.rows() * resized.cols()
                val quad = findLargestQuadrilateral(contours, resizedArea, resized.size())

                if (quad != null) {
                    // Calculate confidence on resized image area (before scaling)
                    val confidence = calculateConfidence(quad, resizedArea)

                    // Scale corners back to original image size
                    val scaledCorners = scaleCorners(quad, resized.size(), image.size())

                    Log.d(TAG, "Detected quad with confidence: $confidence (corners: ${quad.map { "(${it.x.toInt()},${it.y.toInt()})" }})")
                    Log.d(TAG, "Scaled corners: ${scaledCorners.map { "(${it.x.toInt()},${it.y.toInt()})" }}")

                    DocumentEdges(
                        topLeft = scaledCorners[0],
                        topRight = scaledCorners[1],
                        bottomRight = scaledCorners[2],
                        bottomLeft = scaledCorners[3],
                        confidence = confidence,
                        mode = DetectionMode.AUTO,
                    )
                } else {
                    Log.d(TAG, "No quadrilateral found")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting edges", e)
                null
            } finally {
                // Clean up resources
                image?.release()
                resized?.release()
                gray?.release()
                edges?.release()
                hierarchy?.release()
                contours.forEach { it.release() }
            }
        }

    /**
     * Apply perspective transform to crop and straighten document.
     */
    actual suspend fun applyPerspectiveTransform(
        imagePath: String,
        corners: DocumentEdges?,
        rotation: Int,
    ): String =
        withContext(Dispatchers.Default) {
            // If corners is null, return original image path (no transformation)
            if (corners == null) {
                Log.d(TAG, "No corners provided, returning original image")
                return@withContext imagePath
            }
            var image: Mat? = null
            var warped: Mat? = null
            var rotated: Mat? = null
            var srcPoints: MatOfPoint2f? = null
            var dstPoints: MatOfPoint2f? = null
            var transformMatrix: Mat? = null

            try {
                // Load image
                image = Imgcodecs.imread(imagePath)
                if (image == null || image.empty()) {
                    throw IllegalArgumentException("Failed to load image: $imagePath")
                }

                // Order corners properly (TL, TR, BR, BL)
                val orderedCorners = orderCorners(corners)

                // Calculate destination dimensions
                val destWidth =
                    maxOf(
                        distance(orderedCorners.topLeft, orderedCorners.topRight),
                        distance(orderedCorners.bottomLeft, orderedCorners.bottomRight),
                    ).toInt()

                val destHeight =
                    maxOf(
                        distance(orderedCorners.topLeft, orderedCorners.bottomLeft),
                        distance(orderedCorners.topRight, orderedCorners.bottomRight),
                    ).toInt()

                Log.d(TAG, "Transform to: ${destWidth}x$destHeight")

                // Source and destination points
                srcPoints =
                    MatOfPoint2f(
                        org.opencv.core.Point(orderedCorners.topLeft.x.toDouble(), orderedCorners.topLeft.y.toDouble()),
                        org.opencv.core.Point(orderedCorners.topRight.x.toDouble(), orderedCorners.topRight.y.toDouble()),
                        org.opencv.core.Point(orderedCorners.bottomRight.x.toDouble(), orderedCorners.bottomRight.y.toDouble()),
                        org.opencv.core.Point(orderedCorners.bottomLeft.x.toDouble(), orderedCorners.bottomLeft.y.toDouble()),
                    )

                dstPoints =
                    MatOfPoint2f(
                        org.opencv.core.Point(0.0, 0.0),
                        org.opencv.core.Point(destWidth.toDouble(), 0.0),
                        org.opencv.core.Point(destWidth.toDouble(), destHeight.toDouble()),
                        org.opencv.core.Point(0.0, destHeight.toDouble()),
                    )

                // Get perspective transform matrix
                transformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

                // Apply warp
                warped = Mat()
                Imgproc.warpPerspective(
                    image,
                    warped,
                    transformMatrix,
                    Size(destWidth.toDouble(), destHeight.toDouble()),
                )

                // Apply rotation if needed
                rotated = applyRotation(warped, rotation)

                // Generate output path
                val outputPath = generateOutputPath(imagePath)

                // Save with high quality
                Imgcodecs.imwrite(
                    outputPath,
                    rotated,
                    MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 85),
                )

                Log.d(TAG, "Saved cropped image to: $outputPath")

                outputPath
            } catch (e: Exception) {
                Log.e(TAG, "Error applying perspective transform", e)
                throw e
            } finally {
                // Cleanup
                image?.release()
                warped?.release()
                rotated?.release()
                srcPoints?.release()
                dstPoints?.release()
                transformMatrix?.release()
            }
        }

    /**
     * Get default corners for manual mode (full image with small margin).
     */
    actual fun getDefaultCorners(
        imageWidth: Int,
        imageHeight: Int,
    ): DocumentEdges {
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

    /**
     * Get image dimensions without loading full bitmap.
     */
    actual fun getImageSize(imagePath: String): ImageSize {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(imagePath, options)

        // BitmapFactory returns width x height (same as OpenCV cols x rows)
        Log.d(TAG, "BitmapFactory size: ${options.outWidth}x${options.outHeight} (WxH)")
        return ImageSize(width = options.outWidth, height = options.outHeight)
    }

    // ========== Private Helper Methods ==========

    private fun preprocessImage(image: Mat): Mat {
        val maxDimension = 1024.0
        val scale = maxDimension / maxOf(image.rows(), image.cols())

        return if (scale < 1.0) {
            val resized = Mat()
            Imgproc.resize(
                image,
                resized,
                Size(image.cols() * scale, image.rows() * scale),
            )
            resized
        } else {
            image.clone()
        }
    }

    private fun findLargestQuadrilateral(
        contours: List<MatOfPoint>,
        imageArea: Int,
        imageSize: Size,
    ): List<Point>? {
        val minArea = imageArea * 0.05 // Must be at least 5% of image

        var largestQuad: List<Point>? = null
        var maxArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) continue

            // Approximate contour to polygon
            val contour2f = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

            // Check if it's a quadrilateral
            if (approx.rows() == 4) {
                val approxPoints = MatOfPoint(*approx.toArray())
                if (Imgproc.isContourConvex(approxPoints)) {
                    if (area > maxArea && isValidQuadrilateral(approx.toList(), imageSize)) {
                        maxArea = area
                        largestQuad =
                            approx.toArray().map {
                                Point(it.x.toFloat(), it.y.toFloat())
                            }
                    }
                }
                approxPoints.release()
            }

            contour2f.release()
            approx.release()
        }

        return largestQuad
    }

    private fun isValidQuadrilateral(
        points: List<org.opencv.core.Point>,
        imageSize: Size,
    ): Boolean {
        // Check that all points are within image bounds
        for (pt in points) {
            if (pt.x < 0 || pt.x > imageSize.width || pt.y < 0 || pt.y > imageSize.height) {
                return false
            }
        }

        // Check that quadrilateral doesn't have very acute angles
        // (would indicate false detection)
        // This is a simple check - could be enhanced
        return true
    }

    private fun scaleCorners(
        corners: List<Point>,
        fromSize: Size,
        toSize: Size,
    ): List<Point> {
        val scaleX = toSize.width / fromSize.width
        val scaleY = toSize.height / fromSize.height

        return corners.map { corner ->
            Point(
                x = (corner.x * scaleX).toFloat(),
                y = (corner.y * scaleY).toFloat(),
            )
        }
    }

    private fun calculateConfidence(
        corners: List<Point>,
        imageArea: Int,
    ): DetectionConfidence {
        // Calculate area of detected quad
        val quadArea = calculatePolygonArea(corners)
        val ratio = quadArea / imageArea

        return when {
            ratio > 0.7 -> DetectionConfidence.HIGH // >70% threshold for auto-detection
            ratio > 0.4 -> DetectionConfidence.MEDIUM
            else -> DetectionConfidence.LOW
        }
    }

    private fun calculatePolygonArea(points: List<Point>): Double {
        var area = 0.0
        val n = points.size

        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }

        return kotlin.math.abs(area) / 2.0
    }

    private fun orderCorners(corners: DocumentEdges): DocumentEdges {
        // Corners might not be in the correct order, so we need to sort them
        val points =
            listOf(
                corners.topLeft,
                corners.topRight,
                corners.bottomRight,
                corners.bottomLeft,
            )

        // Sort by quadrant
        val topLeft = points.minByOrNull { it.x + it.y }!!
        val bottomRight = points.maxByOrNull { it.x + it.y }!!
        val topRight =
            points.filter { it != topLeft && it != bottomRight }
                .minByOrNull { it.y - it.x }!!
        val bottomLeft = points.first { it != topLeft && it != bottomRight && it != topRight }

        return DocumentEdges(
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft,
            confidence = corners.confidence,
            mode = corners.mode,
        )
    }

    private fun distance(
        p1: Point,
        p2: Point,
    ): Float {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }

    private fun applyRotation(
        image: Mat,
        rotation: Int,
    ): Mat {
        return when (rotation) {
            90 -> {
                val rotated = Mat()
                Core.rotate(image, rotated, Core.ROTATE_90_CLOCKWISE)
                rotated
            }
            180 -> {
                val rotated = Mat()
                Core.rotate(image, rotated, Core.ROTATE_180)
                rotated
            }
            270 -> {
                val rotated = Mat()
                Core.rotate(image, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
                rotated
            }
            else -> image.clone()
        }
    }

    private fun generateOutputPath(inputPath: String): String {
        val file = File(inputPath)
        val dir = file.parentFile
        val filename = file.nameWithoutExtension
        val timestamp = System.currentTimeMillis()

        return File(dir, "${filename}_cropped_$timestamp.jpg").absolutePath
    }

    companion object {
        private const val TAG = "EdgeDetector"
    }
}
