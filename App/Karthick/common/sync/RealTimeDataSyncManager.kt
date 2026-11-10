package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Manages real-time data synchronization via push notifications
 * Invalidates only the caches for the data that changed
 *
 * This manager processes FCM push notifications and triggers targeted
 * data refreshes only for the specific data that changed.
 */
object RealTimeDataSyncManager {
    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Last notification received
    private val _lastNotification = MutableStateFlow<NotificationData?>(null)
    val lastNotification: StateFlow<NotificationData?> = _lastNotification.asStateFlow()

    // Notification counter for debugging
    private val _notificationCount = MutableStateFlow(0)
    val notificationCount: StateFlow<Int> = _notificationCount.asStateFlow()

    // Multiple callbacks for ViewModel reloads after sync completes
    // Supports multiple ViewModels registering simultaneously without overwriting
    private val syncCallbacks = mutableListOf<() -> Unit>()

    /**
     * Register a callback to be invoked when smart sync completes
     * Multiple ViewModels can register without overwriting each other
     */
    fun registerSyncCallback(callback: () -> Unit) {
        if (!syncCallbacks.contains(callback)) {
            syncCallbacks.add(callback)
            println("🔔 Registered sync callback (total: ${syncCallbacks.size})")
        }
    }

    /**
     * Unregister a sync callback
     */
    fun unregisterSyncCallback(callback: () -> Unit) {
        syncCallbacks.remove(callback)
        println("🔕 Unregistered sync callback (total: ${syncCallbacks.size})")
    }

    /**
     * Handle incoming push notification from FCM
     * Called by platform-specific FCM service (Android/iOS)
     *
     * @param changeType Type of data change (document_added, material_ready, deadline_changed, ...)
     * @param data Notification payload data
     */
    fun handleDataChange(
        changeType: String?,
        data: Map<String, String>?,
    ) {
        if (changeType == null) {
            println("⚠️ RealTimeSync: Received notification with null changeType")
            return
        }

        println("📬 RealTimeSync: Processing notification - $changeType")

        val documentId = data?.get("document_id")
        val courseId = data?.get("course_id")

        // Store notification for debugging
        _lastNotification.value =
            NotificationData(
                type = changeType,
                timestamp = Clock.System.now(),
                data = data ?: emptyMap(),
            )
        _notificationCount.value += 1

        when (changeType) {
            // SCANS
            "document_added", "document_deleted" -> {
                SimpleCache.instance.invalidatePattern("documents")
                triggerSmartSync()
            }
            "document_updated" -> {
                documentId?.let { SimpleCache.instance.invalidate("document_$it") }
                SimpleCache.instance.invalidatePattern("documents")
            }

            // COURSES
            "material_ready", "course_updated" -> {
                SimpleCache.instance.invalidatePattern("courses")
                courseId?.let { SimpleCache.instance.invalidatePattern("course_$it") }
            }

            // DEADLINES
            "deadline_changed" -> SimpleCache.instance.invalidatePattern("deadlines")

            // PROFILE AND SETTINGS
            "profile_updated", "settings_updated" -> {
                com.kashyap.wizlyai.session.UserSessionManager.instance.invalidateCache()
                SimpleCache.instance.invalidatePattern("settings")
                SimpleCache.instance.invalidatePattern("profile")
                triggerSmartSync()
            }

            else -> println("⚠️ Unknown notification type: $changeType")
        }
    }

    /**
     * Trigger smart sync in response to FCM notification
     * This checks for updates and invalidates caches to trigger UI refresh
     */
    private fun triggerSmartSync() {
        println("🔄 Smart sync trigger received from FCM notification")
        scope.launch {
            try {
                // Use new BootstrapSyncCoordinator for smart sync
                // This will check if sync is needed and invalidate caches
                BootstrapSyncCoordinator.smartSync(force = true)
                println("✅ Smart sync completed successfully")

                // Trigger ALL registered ViewModel reload callbacks
                println("🔄 Invoking ${syncCallbacks.size} registered callbacks")
                syncCallbacks.forEach { callback ->
                    try {
                        callback.invoke()
                    } catch (e: Exception) {
                        println("❌ Callback invocation failed: ${e.message}")
                    }
                }
                println("✅ All ViewModel reload callbacks invoked")
            } catch (e: Exception) {
                println("❌ Smart sync failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Clear notification history (for testing/debugging)
     */
    fun clearNotifications() {
        _lastNotification.value = null
        _notificationCount.value = 0
        println("🧹 Notification history cleared")
    }

    /**
     * Get notification statistics
     */
    fun getStats(): NotificationStats {
        return NotificationStats(
            totalNotifications = _notificationCount.value,
            lastNotificationType = _lastNotification.value?.type,
            lastNotificationTime = _lastNotification.value?.timestamp,
        )
    }
}

/**
 * Data class representing a notification
 */
data class NotificationData(
    val type: String,
    val timestamp: kotlinx.datetime.Instant,
    val data: Map<String, String>,
)

/**
 * Statistics about notifications received
 */
data class NotificationStats(
    val totalNotifications: Int,
    val lastNotificationType: String?,
    val lastNotificationTime: kotlinx.datetime.Instant?,
)
