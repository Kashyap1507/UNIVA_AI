package com.kashyap.wizlyai.worker

/**
 * iOS implementation of DocumentSyncWorker
 *
 * Note: iOS background sync is handled by IOSBackgroundSyncHandler.swift
 * which uses BGTaskScheduler. This Kotlin wrapper provides API compatibility.
 *
 * The actual implementation is in Swift:
 * - IOSBackgroundSyncHandler.register() - Registers background tasks
 * - IOSBackgroundSyncHandler.schedule() - Schedules background refresh
 * - IOSBackgroundSyncHandler.performSync() - Executes sync
 */
actual object DocumentSyncWorker {
    /**
     * Schedule periodic document sync
     * Delegates to IOSBackgroundSyncHandler.schedule()
     */
    actual fun schedule() {
        println("📅 iOS DocumentSyncWorker: Schedule called")
        println("   Note: Actual scheduling done by IOSBackgroundSyncHandler.swift")
        // IOSBackgroundSyncHandler.schedule() is already called in iOSApp.swift init
    }

    /**
     * Cancel scheduled document sync
     * Cancels BGTaskScheduler tasks
     */
    actual fun cancel() {
        println("🚫 iOS DocumentSyncWorker: Cancel called")
        println("   Note: Cancellation handled by IOSBackgroundSyncHandler.swift")
        // In a full implementation, would call IOSBackgroundSyncHandler.cancel()
    }

    /**
     * Trigger immediate sync (one-time)
     * Calls IOSBackgroundSyncHandler.performSync()
     */
    actual fun triggerSync() {
        println("🔄 iOS DocumentSyncWorker: Trigger sync called")
        println("   Note: Sync triggered via IOSBackgroundSyncHandler.swift")
        // In a full implementation, would call IOSBackgroundSyncHandler.shared.performSync()
    }
}
