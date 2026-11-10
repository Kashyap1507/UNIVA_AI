package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.network.models.BootstrapData

/**
 * Android implementation of BootstrapSyncBridge
 * Android uses BootstrapSyncCoordinator directly, which already handles everything
 * This bridge is mainly for iOS compatibility
 */
internal actual object BootstrapSyncBridge {
    actual fun processBootstrapData(
        bootstrapData: BootstrapData,
        userId: String,
    ) {
        // Android already handles this via BootstrapSyncCoordinator.syncBootstrapData()
        // called from AuthViewModel after successful authentication
        println("🔵 Android: BootstrapSyncBridge.processBootstrapData (no-op, handled by BootstrapSyncCoordinator)")
    }

    actual fun clearAllData() {
        // Android already handles this via BootstrapSyncCoordinator.clearSyncData()
        // called from logout
        println("🔵 Android: BootstrapSyncBridge.clearAllData (no-op, handled by BootstrapSyncCoordinator)")
    }
}
