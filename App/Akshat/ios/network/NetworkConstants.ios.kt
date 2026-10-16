package com.kashyap.wizlyai.network

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

/**
 * iOS-specific implementation to get base URL
 * Returns localhost for simulator, machine IP for physical device
 */
internal actual fun getPlatformSpecificBaseUrl(): String {
    // Check if running on simulator
    // UIDevice.model reports "iPhone" on the simulator too, so rely on the
    // SIMULATOR_UDID env var that the simulator runtime always sets
    val isSimulator =
        NSProcessInfo.processInfo.environment["SIMULATOR_UDID"] != null ||
            UIDevice.currentDevice.model.contains("Simulator", ignoreCase = true)

    return if (isSimulator) {
        NetworkConstants.BaseUrls.IOS_SIMULATOR // localhost:5000
    } else {
        NetworkConstants.BaseUrls.IOS_DEVICE // 192.168.0.78:5000
    }
}

/**
 * iOS-specific implementation to detect debug mode
 * SECURITY: Reads from Info.plist or uses preprocessor directives
 *
 * Returns:
 * - true in debug builds (enables HTTP, local servers)
 * - false in release builds (enforces HTTPS, production servers)
 *
 * Note: This should be configured in Xcode build settings:
 * - Debug configuration: Set "IS_DEBUG" = YES in Info.plist
 * - Release configuration: Set "IS_DEBUG" = NO in Info.plist
 * Or use #if DEBUG in Swift bridge
 */
internal actual fun getPlatformDebugFlag(): Boolean {
    return try {
        // Try to read from Info.plist
        val isDebug = NSBundle.mainBundle.objectForInfoDictionaryKey("IS_DEBUG")

        when (isDebug) {
            is Boolean -> isDebug
            is String -> isDebug.lowercase() in listOf("yes", "true", "1")
            is Number -> isDebug.toInt() != 0
            else -> {
                // If IS_DEBUG not set in Info.plist, default to false (production)
                // For development, ensure IS_DEBUG is set in your Info.plist
                false
            }
        }
    } catch (e: Exception) {
        // Security: Default to production (false) if detection fails
        false
    }
}
