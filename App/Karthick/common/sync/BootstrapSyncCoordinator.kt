package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.BootstrapData

/**
 * Platform-agnostic interface for bootstrap data synchronization
 *
 * This expect/actual pattern allows AuthViewModel (in commonMain) to trigger
 * platform-specific sync operations without tight coupling to Android-specific code.
 *
 * Implementations:
 * - Android: Uses SyncCoordinator → SyncRepository → Room database
 * - iOS: Can use similar pattern with CoreData or other iOS storage
 */
expect object BootstrapSyncCoordinator {
    /**
     * Sync bootstrap data after authentication
     *
     * This is called by AuthViewModel after successful login/signup.
     * It orchestrates the entire sync process:
     * 1. User entity sync
     * 2. Bootstrap data sync (app settings, usage)
     * 3. Cache invalidation
     *
     * @param bootstrapData Bootstrap data from auth response
     * @param user Authenticated user
     */
    fun syncBootstrapData(
        bootstrapData: BootstrapData,
        user: AuthUser,
    )

    /**
     * Clear all synced data (called on logout)
     */
    suspend fun clearSyncData()

    /**
     * Trigger smart sync to check for updates
     *
     * This can be called:
     * - In response to FCM push notification
     * - Periodically (e.g., every 24 hours)
     * - When app comes to foreground
     * - When user explicitly requests refresh
     *
     * Smart sync checks if sync is needed based on last sync timestamp
     * and only invalidates caches to trigger UI refresh if needed.
     *
     * @param force Force sync even if not needed based on time interval
     */
    suspend fun smartSync(force: Boolean = false)

    /**
     * Sync app settings to backend
     *
     * This uploads pending settings changes to the backend server.
     * Called after:
     * - User changes a setting (cloud sync, cellular sync, scan quality)
     * - User taps "Sync Now" button
     * - Network becomes available (after offline changes)
     */
    fun syncAppSettings()

    /**
     * Sync documents to backend and wait for completion
     *
     * This is a suspend function that ensures documents are uploaded to the backend
     * before returning. Useful when you need to guarantee the document exists on the
     * server before calling other APIs (e.g., "Check my work").
     *
     * @return Result with sync stats or error
     */
    suspend fun syncDocumentsAndWait(): Result<Unit>
}
