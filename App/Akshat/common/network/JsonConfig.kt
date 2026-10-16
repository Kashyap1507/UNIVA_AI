package com.kashyap.wizlyai.network

import kotlinx.serialization.json.Json

/**
 * Shared Json configuration for all JSON serialization/deserialization in the app
 */
object JsonConfig {
    /**
     * Shared Json instance with full configuration
     * Use this for all JSON operations in the app
     */
    val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true // Handle null/invalid values gracefully
            encodeDefaults = false // Don't encode default values
        }
}
