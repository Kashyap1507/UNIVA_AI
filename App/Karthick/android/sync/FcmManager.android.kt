package com.kashyap.wizlyai.sync

import android.os.Build

/**
 * Android implementation of platform-specific FCM functions
 */

/**
 * Get Android device model name
 */
internal actual fun getPlatformDeviceName(): String {
    return "${Build.MANUFACTURER} ${Build.MODEL}"
}

/**
 * Get platform type - always "android"
 */
internal actual fun getPlatformType(): String {
    return "android"
}

/**
 * Get app version from BuildConfig
 * Falls back to "1.0.0" if not available
 */
internal actual fun getAppVersion(): String {
    return try {
        // In production, this should come from BuildConfig.VERSION_NAME
        // For now, return a default version
        "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}
