package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.network.models.BootstrapData

/**
 * Platform-specific bridge for bootstrap sync operations
 * Android: Uses SyncCoordinator
 * iOS: Uses IOSBootstrapSyncManager via Swift bridge
 */
internal expect object BootstrapSyncBridge {
    /**
     * Process bootstrap data and persist to local storage
     */
    fun processBootstrapData(
        bootstrapData: BootstrapData,
        userId: String,
    )

    /**
     * Clear all synced data
     */
    fun clearAllData()
}
