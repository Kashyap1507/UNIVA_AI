package com.kashyap.wizlyai.data.models

import kotlinx.serialization.Serializable

/**
 * Scan quality presets for document scanning
 *
 * Determines the resolution and file size of scanned documents.
 * Higher quality = larger file size but better image quality.
 */
@Serializable
enum class ScanQuality(
    val displayName: String,
    val width: Int,
    val height: Int,
    val estimatedSizeMB: Double,
    val isPremium: Boolean = false,
) {
    /**
     * Small quality - Fast upload, basic quality
     * Best for: Quick scans, low storage usage
     */
    SMALL(
        displayName = "Small",
        width = 1024,
        height = 768,
        estimatedSizeMB = 0.5,
    ),

    /**
     * Standard quality - Balanced quality and size
     * Best for: General purpose scanning
     */
    STANDARD(
        displayName = "Standard",
        width = 1920,
        height = 1440,
        estimatedSizeMB = 1.5,
    ),

    /**
     * Large quality - High quality scans
     * Best for: Important documents, detailed content
     */
    LARGE(
        displayName = "Large",
        width = 2592,
        height = 1936,
        estimatedSizeMB = 3.0,
    ),

    /**
     * HD quality - Premium quality scans
     * Best for: Professional documents, archival
     */
    HD(
        displayName = "HD",
        width = 3264,
        height = 2448,
        estimatedSizeMB = 5.0,
        isPremium = true,
    ),
    ;

    /**
     * Get formatted dimension string
     * Example: "2592px X 1936px"
     */
    fun getDimensionString(): String {
        return "${width}px X ${height}px"
    }

    /**
     * Get formatted file size string
     * Example: "~3.0 MB per page"
     */
    fun getFileSizeString(): String {
        return "~$estimatedSizeMB MB per page"
    }

    companion object {
        /**
         * Default scan quality
         */
        val DEFAULT = LARGE

        /**
         * Get quality by slider position (0-3)
         */
        fun fromSliderPosition(position: Int): ScanQuality {
            return when (position) {
                0 -> SMALL
                1 -> STANDARD
                2 -> LARGE
                3 -> HD
                else -> DEFAULT
            }
        }

        /**
         * Get slider position for quality (0-3)
         */
        fun getSliderPosition(quality: ScanQuality): Int {
            return when (quality) {
                SMALL -> 0
                STANDARD -> 1
                LARGE -> 2
                HD -> 3
            }
        }
    }
}
