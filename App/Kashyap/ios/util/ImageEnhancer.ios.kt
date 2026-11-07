package com.kashyap.wizlyai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.Foundation.*
import platform.UIKit.*

/**
 * iOS implementation of document image enhancement using CoreImage.
 *
 * Provides professional document processing techniques:
 * - Noise reduction (CINoiseReduction + CIMedianFilter) - matches Android bilateral filter
 * - Illumination normalization (CIToneCurve + CIColorControls) - CLAHE-like adaptive processing
 * - Text enhancement (CIColorMonochrome for thresholding)
 * - Sharpening (CIUnsharpMask)
 *
 * Key improvements to match Android's OpenCV implementation:
 * - Removed exposure boost to prevent over-brightening
 * - Implemented tone curve for adaptive shadow/highlight adjustment
 * - Reduced contrast and saturation for more balanced output
 * - Fine-tuned noise reduction to preserve text edges
 */
@OptIn(ExperimentalForeignApi::class)
actual class ImageEnhancer {
    private val context = CIContext()

    /**
     * Enhance a document image using CoreImage processing pipeline.
     *
     * CURRENTLY DISABLED: Returns original image without enhancement.
     * TODO: Re-enable once brightness issues are resolved.
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
            try {
                println("📸 ImageEnhancer.ios: Image enhancement DISABLED - returning original image")
                println("   Input path: $inputPath")

                // TEMPORARILY DISABLED: Skip all enhancement and return original
                // This prevents the over-brightening issue while we investigate
                return@withContext inputPath

            /* DISABLED ENHANCEMENT CODE - KEEP FOR FUTURE USE

            // Load image
            val inputImage = loadImage(inputPath)
                ?: throw IllegalArgumentException("Failed to load image: $inputPath")

            val width = CGRectGetWidth(inputImage.extent)
            val height = CGRectGetHeight(inputImage.extent)
            println("   Image size: ${width}x${height}")

            // Apply enhancement pipeline based on mode
            val enhanced = when (mode) {
                EnhancementMode.LIGHT -> {
                    // Light: denoise + normalize
                    val denoised = denoise(inputImage)
                    normalizeIllumination(denoised)
                }

                EnhancementMode.BALANCED -> {
                    // Balanced: denoise + normalize + sharpen
                    val denoised = denoise(inputImage)
                    val normalized = normalizeIllumination(denoised)
                    sharpenText(normalized)
                }

                EnhancementMode.AGGRESSIVE -> {
                    // Aggressive: denoise + normalize + threshold + sharpen
                    val denoised = denoise(inputImage)
                    val normalized = normalizeIllumination(denoised)
                    val thresholded = adaptiveThreshold(normalized)
                    sharpenText(thresholded)
                }
            }

            // Generate output path
            val outputPath = generateOutputPath(inputPath, "_enhanced")

            // Save enhanced image
            saveImage(enhanced, outputPath)

            println("✅ ImageEnhancer.ios: Saved enhanced image to $outputPath")
            outputPath

            END DISABLED CODE */
            } catch (e: Exception) {
                println("❌ ImageEnhancer.ios: Enhancement failed: ${e.message}")
                // Return original path on failure
                inputPath
            }
        }

    /**
     * Load image from file path.
     */
    private fun loadImage(path: String): CIImage? {
        val url = NSURL.fileURLWithPath(path)
        return CIImage.imageWithContentsOfURL(url)
    }

    /**
     * Save CIImage to file path.
     */
    private fun saveImage(
        image: CIImage,
        path: String,
    ) {
        // Convert CIImage to CGImage
        val cgImage =
            context.createCGImage(image, image.extent)
                ?: throw IllegalStateException("Failed to create CGImage")

        // Create UIImage and get JPEG data
        val uiImage = UIImage.imageWithCGImage(cgImage)
        val jpegData =
            UIImageJPEGRepresentation(uiImage, 0.92) // 92% quality
                ?: throw IllegalStateException("Failed to create JPEG data")

        // Write to file
        val success = jpegData.writeToFile(path, atomically = true)
        if (!success) {
            throw IllegalStateException("Failed to write image to: $path")
        }
    }

    /**
     * Apply noise reduction filters.
     * Combines CINoiseReduction with CIMedianFilter for bilateral-like effect.
     * Tuned to match Android's bilateral filter behavior.
     */
    private fun denoise(image: CIImage): CIImage {
        // Apply CINoiseReduction filter
        val noiseReduction =
            CIFilter.filterWithName("CINoiseReduction")
                ?: return image

        noiseReduction.setValue(image, forKey = "inputImage")
        noiseReduction.setValue(0.008, forKey = "inputNoiseLevel") // Further reduced to preserve content and prevent softening (was 0.01)
        noiseReduction.setValue(0.7, forKey = "inputSharpness") // Increased to better preserve text edges (was 0.6)

        val step1 = noiseReduction.outputImage ?: image

        // Apply CIMedianFilter for additional smoothing
        val median =
            CIFilter.filterWithName("CIMedianFilter")
                ?: return step1

        median.setValue(step1, forKey = "inputImage")

        println("   Applied noise reduction (matching Android bilateral filter)")
        return median.outputImage ?: step1
    }

    /**
     * Normalize illumination across document using CLAHE-like processing.
     * Removes exposure boost and uses adaptive local contrast enhancement.
     */
    private fun normalizeIllumination(image: CIImage): CIImage {
        // Step 1: Apply local tone curve adjustment (simulates CLAHE's local adaptation)
        val toneCurve =
            CIFilter.filterWithName("CIToneCurve")
                ?: return applyFallbackNormalization(image)

        toneCurve.setValue(image, forKey = "inputImage")

        // Configure tone curve for adaptive shadow/highlight adjustment
        // Point 0: Black point stays black (0,0)
        toneCurve.setValue(CIVector.vectorWithX(0.0, 0.0), forKey = "inputPoint0")
        // Point 1: Lift shadows slightly without over-brightening (0.25, 0.28)
        toneCurve.setValue(CIVector.vectorWithX(0.25, 0.28), forKey = "inputPoint1")
        // Point 2: Keep midtones balanced (0.5, 0.5)
        toneCurve.setValue(CIVector.vectorWithX(0.5, 0.5), forKey = "inputPoint2")
        // Point 3: Slightly compress highlights to prevent whitening (0.75, 0.72)
        toneCurve.setValue(CIVector.vectorWithX(0.75, 0.72), forKey = "inputPoint3")
        // Point 4: White point stays white (1.0, 1.0)
        toneCurve.setValue(CIVector.vectorWithX(1.0, 1.0), forKey = "inputPoint4")

        val step1 = toneCurve.outputImage ?: image

        // Step 2: Apply subtle contrast enhancement (matching Android's CLAHE clip limit)
        val colorControls =
            CIFilter.filterWithName("CIColorControls")
                ?: return step1

        colorControls.setValue(step1, forKey = "inputImage")
        colorControls.setValue(1.02, forKey = "inputContrast") // Reduced from 1.05 to prevent over-contrast
        colorControls.setValue(1.0, forKey = "inputBrightness") // Neutral brightness (no exposure boost)
        colorControls.setValue(1.05, forKey = "inputSaturation") // Reduced from 1.1 for more neutral colors

        println("   Applied CLAHE-like illumination normalization (no exposure boost)")
        return colorControls.outputImage ?: step1
    }

    /**
     * Fallback normalization if CIToneCurve is unavailable.
     * Uses minimal contrast adjustment only.
     */
    private fun applyFallbackNormalization(image: CIImage): CIImage {
        val colorControls =
            CIFilter.filterWithName("CIColorControls")
                ?: return image

        colorControls.setValue(image, forKey = "inputImage")
        colorControls.setValue(1.02, forKey = "inputContrast")
        colorControls.setValue(1.0, forKey = "inputBrightness")
        colorControls.setValue(1.05, forKey = "inputSaturation")

        println("   Applied fallback normalization")
        return colorControls.outputImage ?: image
    }

    /**
     * Apply adaptive threshold for high-contrast black/white output.
     * Used in AGGRESSIVE mode for handwritten notes and poor lighting.
     */
    private fun adaptiveThreshold(image: CIImage): CIImage {
        // Convert to monochrome (grayscale with threshold effect)
        val monochrome =
            CIFilter.filterWithName("CIColorMonochrome")
                ?: return image

        monochrome.setValue(image, forKey = "inputImage")
        monochrome.setValue(CIColor.grayColor(), forKey = "inputColor")
        monochrome.setValue(1.0, forKey = "inputIntensity")

        val gray = monochrome.outputImage ?: image

        // Increase contrast dramatically to create threshold effect
        val colorControls =
            CIFilter.filterWithName("CIColorControls")
                ?: return gray

        colorControls.setValue(gray, forKey = "inputImage")
        colorControls.setValue(2.5, forKey = "inputContrast") // Very high contrast
        colorControls.setValue(0.1, forKey = "inputBrightness") // Slight darkening
        colorControls.setValue(0.0, forKey = "inputSaturation") // Pure grayscale

        println("   Applied adaptive threshold")
        return colorControls.outputImage ?: gray
    }

    /**
     * Sharpen text using unsharp mask.
     * Enhances edges and makes text more readable.
     */
    private fun sharpenText(image: CIImage): CIImage {
        val sharpen =
            CIFilter.filterWithName("CIUnsharpMask")
                ?: return image

        sharpen.setValue(image, forKey = "inputImage")
        sharpen.setValue(2.5, forKey = "inputRadius") // Blur radius for mask
        sharpen.setValue(0.5, forKey = "inputIntensity") // Sharpening intensity

        println("   Applied unsharp mask sharpening")
        return sharpen.outputImage ?: image
    }

    /**
     * Generate output file path with suffix.
     */
    private fun generateOutputPath(
        inputPath: String,
        suffix: String,
    ): String {
        val url = NSURL.fileURLWithPath(inputPath)
        val filename = url.lastPathComponent?.substringBeforeLast(".") ?: "document"
        val timestamp = NSDate().timeIntervalSince1970.toLong()
        val tempDir = NSTemporaryDirectory()

        return "${tempDir}${filename}${suffix}_$timestamp.jpg"
    }
}
