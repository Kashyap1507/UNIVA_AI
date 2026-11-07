package com.kashyap.wizlyai.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.io.File

/**
 * Android implementation of document image enhancement using OpenCV.
 *
 * Provides industry-standard document processing techniques:
 * - Noise reduction (bilateral filtering)
 * - Illumination normalization (CLAHE)
 * - Text enhancement (adaptive thresholding)
 * - Sharpening (unsharp masking)
 *
 * Similar to CamScanner's "Magic Color" feature.
 */
actual class ImageEnhancer(private val context: Context) {
    /**
     * Enhance a document image using OpenCV processing pipeline.
     *
     * @param inputPath Absolute path to the input image
     * @param mode Enhancement intensity level
     * @return Path to the enhanced image
     */
    actual suspend fun enhanceDocument(
        inputPath: String,
        mode: EnhancementMode,
    ): String =
        withContext(Dispatchers.Default) {
            var mat: Mat? = null
            var enhanced: Mat? = null

            try {
                // Load image
                mat = Imgcodecs.imread(inputPath)
                if (mat == null || mat.empty()) {
                    Log.e(TAG, "Failed to load image: $inputPath")
                    throw IllegalArgumentException("Failed to load image: $inputPath")
                }

                val width = mat.cols()
                val height = mat.rows()
                Log.d(TAG, "Enhancing image: ${width}x$height, mode: $mode")

                // Apply enhancement pipeline based on mode
                enhanced =
                    when (mode) {
                        EnhancementMode.LIGHT -> {
                            // Light: denoise + normalize
                            val denoised = denoise(mat)
                            val normalized = normalizeIllumination(denoised)
                            denoised.release()
                            normalized
                        }

                        EnhancementMode.BALANCED -> {
                            // Balanced: denoise + normalize + sharpen
                            val denoised = denoise(mat)
                            val normalized = normalizeIllumination(denoised)
                            val sharpened = sharpenText(normalized)
                            denoised.release()
                            normalized.release()
                            sharpened
                        }

                        EnhancementMode.AGGRESSIVE -> {
                            // Aggressive: denoise + normalize + threshold + sharpen
                            val denoised = denoise(mat)
                            val normalized = normalizeIllumination(denoised)
                            val thresholded = adaptiveThreshold(normalized)
                            val sharpened = sharpenText(thresholded)
                            denoised.release()
                            normalized.release()
                            thresholded.release()
                            sharpened
                        }
                    }

                // Generate output path
                val outputPath = generateOutputPath(inputPath, "_enhanced")

                // Save with high quality (92% JPEG quality)
                val success =
                    Imgcodecs.imwrite(
                        outputPath,
                        enhanced,
                        MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 92),
                    )

                if (!success) {
                    throw IllegalStateException("Failed to save enhanced image to: $outputPath")
                }

                Log.d(TAG, "Saved enhanced image to: $outputPath")
                outputPath
            } catch (e: Exception) {
                Log.e(TAG, "Error enhancing document", e)
                throw e
            } finally {
                // Always clean up OpenCV resources
                mat?.release()
                enhanced?.release()
            }
        }

    /**
     * Remove noise while preserving edges using bilateral filtering.
     * Superior to Gaussian blur for document processing.
     */
    private fun denoise(mat: Mat): Mat {
        val denoised = Mat()

        // Bilateral filter parameters:
        // - d = 9: Diameter of pixel neighborhood (larger = more blur)
        // - sigmaColor = 75: Filter sigma in color space (larger = more colors mixed)
        // - sigmaSpace = 75: Filter sigma in coordinate space (larger = farther pixels influence)
        Imgproc.bilateralFilter(
            mat,
            denoised,
            9, // Neighborhood diameter
            75.0, // Color sigma
            75.0, // Space sigma
        )

        Log.d(TAG, "Applied bilateral filter denoising")
        return denoised
    }

    /**
     * Normalize illumination across document using CLAHE.
     * Removes shadows and uneven lighting.
     */
    private fun normalizeIllumination(mat: Mat): Mat {
        // Convert to LAB color space (better for illumination processing)
        val lab = Mat()
        Imgproc.cvtColor(mat, lab, Imgproc.COLOR_BGR2Lab)

        // Split LAB channels (L = luminance, A = green-red, B = blue-yellow)
        val labChannels = mutableListOf<Mat>()
        Core.split(lab, labChannels)

        // Apply CLAHE only to L channel (luminance)
        val clahe =
            Imgproc.createCLAHE(
                1.5, // Clip limit (reduced from 2.0 for more natural appearance)
                Size(8.0, 8.0), // Tile grid size (smaller = more local adaptation)
            )

        val lChannel = labChannels[0]
        val lEqualized = Mat()
        clahe.apply(lChannel, lEqualized)

        // Replace L channel with equalized version
        labChannels[0] = lEqualized

        // Merge channels back
        val labEqualized = Mat()
        Core.merge(labChannels, labEqualized)

        // Convert back to BGR
        val result = Mat()
        Imgproc.cvtColor(labEqualized, result, Imgproc.COLOR_Lab2BGR)

        // Clean up
        lab.release()
        lChannel.release()
        lEqualized.release()
        labEqualized.release()
        labChannels.forEach { it.release() }

        Log.d(TAG, "Applied CLAHE illumination normalization")
        return result
    }

    /**
     * Apply adaptive thresholding for clear black-and-white document.
     * Creates crisp text on white background (like CamScanner).
     */
    private fun adaptiveThreshold(mat: Mat): Mat {
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply adaptive threshold
        // ADAPTIVE_THRESH_GAUSSIAN_C: threshold value is weighted sum of neighborhood
        // THRESH_BINARY: pixel > threshold → 255 (white), else → 0 (black)
        val thresholded = Mat()
        Imgproc.adaptiveThreshold(
            gray,
            thresholded,
            255.0, // Max value for binary output
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            15, // Block size (neighborhood, must be odd)
            10.0, // Constant subtracted from weighted mean
        )

        // Optional: Remove small noise specks using morphological opening
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        val cleaned = Mat()
        Imgproc.morphologyEx(thresholded, cleaned, Imgproc.MORPH_OPEN, kernel)

        // Convert back to BGR for consistency
        val result = Mat()
        Imgproc.cvtColor(cleaned, result, Imgproc.COLOR_GRAY2BGR)

        // Clean up
        gray.release()
        thresholded.release()
        kernel.release()
        cleaned.release()

        Log.d(TAG, "Applied adaptive threshold")
        return result
    }

    /**
     * Sharpen text using unsharp masking technique.
     * Enhances edge clarity and text readability.
     */
    private fun sharpenText(mat: Mat): Mat {
        // Create blurred version
        val blurred = Mat()
        Imgproc.GaussianBlur(mat, blurred, Size(0.0, 0.0), 3.0)

        // Unsharp mask: sharpened = original + (original - blurred) * amount
        // Using addWeighted: dst = src1 * alpha + src2 * beta + gamma
        // Formula: sharpened = mat * 1.5 + blurred * (-0.5) + 0
        val sharpened = Mat()
        Core.addWeighted(
            mat, // Original image
            1.5, // Alpha (original weight, >1.0 for enhancement)
            blurred, // Blurred image
            -0.5, // Beta (negative to subtract blur)
            0.0, // Gamma (constant offset)
            sharpened,
        )

        // Clean up
        blurred.release()

        Log.d(TAG, "Applied unsharp mask sharpening")
        return sharpened
    }

    /**
     * Alternative: Non-local means denoising (slower but higher quality)
     * Use this for very noisy images if performance allows.
     */
    private fun denoiseNonLocalMeans(mat: Mat): Mat {
        val denoised = Mat()

        // Non-local means denoising - examines similar patches across image
        Photo.fastNlMeansDenoisingColored(
            mat,
            denoised,
            3f, // Filter strength (higher = more denoising)
            3f, // Color filter strength
            7, // Template window size (must be odd)
            21, // Search window size (must be odd)
        )

        Log.d(TAG, "Applied non-local means denoising")
        return denoised
    }

    /**
     * Generate output file path with suffix.
     */
    private fun generateOutputPath(
        inputPath: String,
        suffix: String,
    ): String {
        val file = File(inputPath)
        val dir = file.parentFile ?: context.cacheDir
        val filename = file.nameWithoutExtension
        val timestamp = System.currentTimeMillis()

        return File(dir, "${filename}${suffix}_$timestamp.jpg").absolutePath
    }

    companion object {
        private const val TAG = "ImageEnhancer"
    }
}

/**
 * Extension functions for cleaner pipeline chaining
 */
private fun Mat.denoise(enhancer: ImageEnhancer): Mat {
    // Note: Can't access private methods, so these are unused
    // Kept for potential future public API
    return this
}

private fun Mat.normalizeIllumination(enhancer: ImageEnhancer): Mat {
    return this
}

private fun Mat.sharpenText(enhancer: ImageEnhancer): Mat {
    return this
}
