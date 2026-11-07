package com.kashyap.wizlyai.vision

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class DetectionMode {
    AUTO,
    MANUAL,
}

enum class DetectionConfidence {
    HIGH, // > 70% coverage
    MEDIUM, // 40-70% coverage
    LOW, // < 40% coverage
    FAILED, // No document detected
}

@Serializable
data class DocumentEdges(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
    val confidence: DetectionConfidence,
    val mode: DetectionMode,
) {
    /**
     * Serialize to JSON for caching
     */
    fun toJson(): String {
        return Json.encodeToString(this)
    }

    companion object {
        /**
         * Deserialize from JSON
         */
        fun fromJson(json: String): DocumentEdges? {
            return try {
                Json.decodeFromString<DocumentEdges>(json)
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ImageSize(
    val width: Int,
    val height: Int,
)
