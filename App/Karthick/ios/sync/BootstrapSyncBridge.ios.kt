package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.ios.IOSCallbackBridge
import com.kashyap.wizlyai.network.models.BootstrapData

/**
 * iOS implementation of BootstrapSyncBridge
 * Calls Swift IOSBootstrapSyncManager via callbacks
 */
internal actual object BootstrapSyncBridge {
    actual fun processBootstrapData(
        bootstrapData: BootstrapData,
        userId: String,
    ) {
        println("🔵 iOS: BootstrapSyncBridge.processBootstrapData")
        println("   User ID: $userId")

        // Call Swift implementation via callback bridge
        val callbacks = IOSCallbackBridge.getBootstrapSyncCallbacks()
        if (callbacks != null) {
            println("📞 iOS: Calling Swift syncBootstrapData via callback...")
            callbacks.syncBootstrapData(bootstrapData, userId)
            println("✅ iOS: Bootstrap sync callback invoked")
        } else {
            println("⚠️ iOS: Bootstrap sync callbacks not set!")
            println("   Note: Callbacks should be passed to MainViewController")
        }
    }

    actual fun clearAllData() {
        println("🔵 iOS: BootstrapSyncBridge.clearAllData")

        // Call Swift implementation via callback bridge
        val callbacks = IOSCallbackBridge.getBootstrapSyncCallbacks()
        if (callbacks != null) {
            println("📞 iOS: Calling Swift clearSyncData via callback...")
            callbacks.clearSyncData()
            println("✅ iOS: Clear data callback invoked")
        } else {
            println("⚠️ iOS: Bootstrap sync callbacks not set!")
        }
    }
}
