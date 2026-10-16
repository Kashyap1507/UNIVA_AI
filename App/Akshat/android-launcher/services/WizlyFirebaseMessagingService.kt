package com.kashyap.wizlyai.android.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kashyap.wizlyai.sync.FcmManager
import com.kashyap.wizlyai.sync.RealTimeDataSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Firebase Cloud Messaging service for Wizly app
 * Handles incoming push notifications and FCM token refresh
 */
class WizlyFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "WizlyFCMService"

    /**
     * Called when a message is received from FCM
     * Routes notification data to RealTimeDataSyncManager for selective refresh
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "📬 FCM Message received from: ${message.from}")

        // Extract notification data
        val data = message.data
        val changeType = data["type"]

        Log.d(TAG, "📦 Data payload: $data")
        Log.d(TAG, "🔔 Change type: $changeType")

        // Check if this is a data-only notification (silent push)
        if (changeType != null) {
            Log.d(TAG, "🎯 Routing to RealTimeDataSyncManager")

            // Route to RealTimeDataSyncManager for selective data refresh
            RealTimeDataSyncManager.handleDataChange(
                changeType = changeType,
                data = data,
            )
        } else {
            Log.w(TAG, "⚠️ Received notification without change type")
        }

        // Check if there's a notification payload (display notification)
        message.notification?.let { notification ->
            Log.d(TAG, "🔔 Notification payload:")
            Log.d(TAG, "   Title: ${notification.title}")
            Log.d(TAG, "   Body: ${notification.body}")

            // Android automatically displays notifications with notification payload
            // If you want custom notification handling, implement it here
        }
    }

    /**
     * Called when FCM token is refreshed or generated for the first time
     * Registers the new token with backend via FcmManager
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "🔑 New FCM token received: $token")

        // Register token with backend
        serviceScope.launch {
            try {
                val deviceId = getOrCreateDeviceId()
                val deviceName = android.os.Build.MODEL
                val appVersion = getAppVersion()

                Log.d(TAG, "📲 Registering token with backend")
                Log.d(TAG, "   Device ID: $deviceId")
                Log.d(TAG, "   Device: $deviceName")
                Log.d(TAG, "   Version: $appVersion")

                val result =
                    FcmManager.instance.registerToken(
                        deviceId = deviceId,
                        token = token,
                        deviceName = deviceName,
                        platform = "android",
                        appVersion = appVersion,
                    )

                result.fold(
                    onSuccess = { response ->
                        Log.i(TAG, "✅ Token registered successfully: ${response.message}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ Token registration failed: ${error.message}")
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during token registration: ${e.message}", e)
            }
        }
    }

    /**
     * Get or create a unique device ID
     * In production, this should be stored persistently (SharedPreferences)
     */
    private fun getOrCreateDeviceId(): String {
        // TODO: Store device ID in SharedPreferences for persistence
        // For now, generate a unique ID (this will change on app reinstall)
        return try {
            // Try to get from SharedPreferences
            val prefs = getSharedPreferences("wizly_prefs", MODE_PRIVATE)
            val existingId = prefs.getString("device_id", null)

            if (existingId != null) {
                Log.d(TAG, "📱 Using existing device ID: $existingId")
                existingId
            } else {
                // Generate new device ID
                val newId = "android_${UUID.randomUUID()}"
                prefs.edit().putString("device_id", newId).apply()
                Log.d(TAG, "🆕 Generated new device ID: $newId")
                newId
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error getting device ID, using fallback: ${e.message}")
            "android_${UUID.randomUUID()}"
        }
    }

    /**
     * Get app version from BuildConfig or package manager
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not get app version: ${e.message}")
            "1.0.0"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 FCM Service destroyed")
    }
}
