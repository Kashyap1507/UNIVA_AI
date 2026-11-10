package com.kashyap.wizlyai.sync

import platform.UIKit.UIDevice

/**
 * iOS implementation of platform-specific FCM functions
 */

/**
 * Get iOS device model name
 */
internal actual fun getPlatformDeviceName(): String {
    return UIDevice.currentDevice.model
}

/**
 * Get platform type - always "ios"
 */
internal actual fun getPlatformType(): String {
    return "ios"
}

/**
 * Get app version from bundle
 * Falls back to "1.0.0" if not available
 */
internal actual fun getAppVersion(): String {
    return try {
        // In production, this should come from Info.plist CFBundleShortVersionString
        // For now, return a default version
        "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}
