package com.kashyap.wizlyai.util

/**
 * Enhancement modes for different document types
 */
enum class EnhancementMode {
    /**
     * Light enhancement - minimal processing
     * Best for: Already well-lit, high-quality scans
     */
    LIGHT,

    /**
     * Balanced enhancement - moderate processing (recommended)
     * Best for: Most documents, mixed lighting conditions
     */
    BALANCED,

    /**
     * Aggressive enhancement - maximum processing
     * Best for: Poor lighting, low contrast, handwritten notes
     */
    AGGRESSIVE,
}

/**
 * Cross-platform image enhancer for document processing.
 *
 * Android: Full OpenCV implementation with professional enhancement pipeline
 * iOS: Stub implementation (returns input path unchanged)
 */
expect class ImageEnhancer {
    /**
     * Enhance a document image using platform-specific processing.
     *
     * @param inputPath Absolute path to the input image
     * @param mode Enhancement intensity level (LIGHT, BALANCED, AGGRESSIVE)
     * @return Path to the enhanced image
     */
    suspend fun enhanceDocument(
        inputPath: String,
        mode: EnhancementMode = EnhancementMode.BALANCED,
    ): String
}
