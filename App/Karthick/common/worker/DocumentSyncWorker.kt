package com.kashyap.wizlyai.worker

/**
 * Background worker for syncing pending documents to backend
 *
 * Platform-specific implementations:
 * - Android: Uses WorkManager for periodic background sync (every 15 minutes)
 * - iOS: Uses BGTaskScheduler via IOSBackgroundSyncHandler.swift
 *
 * This provides a unified API for scheduling document sync across platforms.
 */
expect object DocumentSyncWorker {
    /**
     * Schedule periodic document sync
     *
     * Android: Schedules WorkManager periodic work
     * iOS: Registers BGTaskScheduler background task
     */
    fun schedule()

    /**
     * Cancel scheduled document sync
     *
     * Android: Cancels WorkManager work
     * iOS: Cancels BGTaskScheduler tasks
     */
    fun cancel()

    /**
     * Trigger immediate sync (one-time)
     *
     * Android: Enqueues one-time Work
     * iOS: Calls IOSBackgroundSyncHandler.performSync()
     */
    fun triggerSync()
}
