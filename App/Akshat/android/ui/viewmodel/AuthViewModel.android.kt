package com.kashyap.wizlyai.ui.viewmodel

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Android-specific implementation for FCM token and device ID retrieval
 */

// Store application context (set in MainActivity or Application class)
internal object AndroidContext {
    var context: Context? = null
}

/**
 * Get FCM token from Firebase
 * Returns null - actual FCM token registration happens in WizlyFirebaseMessagingService
 * which has access to Firebase dependencies in the androidApp module
 */
actual fun getFcmToken(): String? {
    // FCM token registration is handled automatically by WizlyFirebaseMessagingService.onNewToken()
    // This method returns null because the token is registered directly with backend from the service
    return null
}

/**
 * Get unique device ID
 * Uses Android ID or generates a UUID if not available
 */
actual fun getDeviceId(): String {
    return try {
        AndroidContext.context?.let { ctx ->
            val androidId =
                Settings.Secure.getString(
                    ctx.contentResolver,
                    Settings.Secure.ANDROID_ID,
                )
            if (androidId != null && androidId.isNotEmpty()) {
                "android_$androidId"
            } else {
                "android_${UUID.randomUUID()}"
            }
        } ?: "android_${UUID.randomUUID()}"
    } catch (e: Exception) {
        println("⚠️ Error getting device ID: ${e.message}")
        "android_${UUID.randomUUID()}"
    }
}
