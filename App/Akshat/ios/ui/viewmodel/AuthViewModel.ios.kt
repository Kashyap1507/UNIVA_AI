package com.kashyap.wizlyai.ui.viewmodel

import platform.Foundation.NSUUID
import platform.UIKit.UIDevice

/**
 * iOS-specific implementation for FCM token and device ID retrieval
 */

// Store FCM token (set by iOS FCM service)
internal object IOSFcmToken {
    var token: String? = null
}

/**
 * Get FCM token from iOS
 * Returns null if token is not available yet
 * Token should be set by iOS APNs/FCM delegate
 */
actual fun getFcmToken(): String? {
    return IOSFcmToken.token
}

/**
 * Get unique device ID for iOS
 * Uses identifierForVendor or generates a UUID
 */
actual fun getDeviceId(): String {
    return try {
        val vendorId = UIDevice.currentDevice.identifierForVendor?.UUIDString
        if (vendorId != null && vendorId.isNotEmpty()) {
            "ios_$vendorId"
        } else {
            "ios_${NSUUID().UUIDString}"
        }
    } catch (e: Exception) {
        println("⚠️ Error getting device ID: ${e.message}")
        "ios_${NSUUID().UUIDString}"
    }
}
