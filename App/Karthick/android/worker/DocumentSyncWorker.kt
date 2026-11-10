package com.kashyap.wizlyai.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.kashyap.wizlyai.sync.BootstrapSyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Android implementation of DocumentSyncWorker
 * Uses WorkManager for periodic background sync
 *
 * This WorkManager worker runs periodically to upload documents
 * that were created offline or failed to upload previously.
 *
 * Features:
 * - Runs every 15 minutes when device is online
 * - Respects battery and network constraints
 * - Automatic retry on failure with exponential backoff
 * - Doesn't run if no pending documents exist
 *
 * @param context Android context
 * @param params WorkManager parameters
 */
class DocumentSyncWorkerImpl(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    /**
     * Execute sync operation
     *
     * This runs in a background thread managed by WorkManager.
     * It calls BootstrapSyncCoordinator to sync pending documents.
     *
     * @return Result.success() if sync completes (even with partial failures)
     *         Result.retry() if sync should be retried later
     *         Result.failure() if sync fails permanently
     */
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "═══════════════════════════════════════════════════════")
            Log.d(TAG, "📦 DocumentSyncWorker: Starting background sync")
            Log.d(TAG, "═══════════════════════════════════════════════════════")
            Log.d(TAG, "   Run attempt: ${runAttemptCount + 1}")
            Log.d(TAG, "   Is stopped: $isStopped")

            return@withContext try {
                // Trigger document sync via BootstrapSyncCoordinator
                // This is a fire-and-forget call - we don't wait for completion
                BootstrapSyncCoordinator.syncDocuments()

                Log.d(TAG, "✅ DocumentSyncWorker: Sync triggered successfully")
                Log.d(TAG, "═══════════════════════════════════════════════════════")

                // Return success - actual sync happens asynchronously
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "❌ DocumentSyncWorker: Sync failed", e)
                Log.e(TAG, "   Error: ${e.message}")
                Log.e(TAG, "═══════════════════════════════════════════════════════")

                // Retry with exponential backoff (WorkManager handles this)
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Log.d(TAG, "🔄 Scheduling retry (attempt ${runAttemptCount + 2}/$MAX_RETRY_ATTEMPTS)")
                    Result.retry()
                } else {
                    Log.e(TAG, "❌ Max retry attempts reached - giving up")
                    Result.failure()
                }
            }
        }

    companion object {
        private const val TAG = "DocumentSyncWorker"
        private const val WORK_NAME = "document_sync_periodic"
        private const val MAX_RETRY_ATTEMPTS = 5

        // Sync interval configuration
        private const val SYNC_INTERVAL_MINUTES = 15L

        /**
         * Schedule periodic document sync
         *
         * This sets up a recurring background job that runs every 15 minutes
         * to sync pending documents when the device is online.
         *
         * Constraints:
         * - Requires active network connection (WiFi or cellular)
         * - Respects battery saver mode (won't run if battery saver is on)
         * - Only runs when device is idle or charging (optional)
         *
         * @param context Android context
         */
        fun schedule(context: Context) {
            Log.d(TAG, "📅 Scheduling periodic document sync")
            Log.d(TAG, "   Interval: $SYNC_INTERVAL_MINUTES minutes")
            Log.d(TAG, "   Requires: Network connection")

            // Define constraints for when sync should run
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Require active internet
                    .setRequiresBatteryNotLow(true) // Don't drain battery
                    .build()

            // Create periodic work request
            val workRequest =
                PeriodicWorkRequestBuilder<DocumentSyncWorkerImpl>(
                    repeatInterval = SYNC_INTERVAL_MINUTES,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES,
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL, // Exponential backoff on retry
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    )
                    .addTag(TAG) // Tag for easier cancellation/query
                    .build()

            // Enqueue unique periodic work (keeps existing if already scheduled)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
                workRequest,
            )

            Log.d(TAG, "✅ Periodic document sync scheduled successfully")
        }

        /**
         * Cancel scheduled document sync
         *
         * This stops the periodic background sync worker.
         * Call this on logout or when sync is no longer needed.
         *
         * @param context Android context
         */
        fun cancel(context: Context) {
            Log.d(TAG, "🛑 Cancelling periodic document sync")

            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

            Log.d(TAG, "✅ Periodic document sync cancelled")
        }

        /**
         * Check if document sync is currently scheduled
         *
         * @param context Android context
         * @return true if sync worker is scheduled
         */
        fun isScheduled(context: Context): Boolean {
            val workInfos =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .get()

            return workInfos.any { !it.state.isFinished }
        }

        /**
         * Trigger one-time immediate sync
         *
         * This bypasses the periodic schedule and syncs immediately.
         * Useful for manual refresh or when user explicitly requests sync.
         *
         * @param context Android context
         */
        fun syncNow(context: Context) {
            Log.d(TAG, "⚡ Triggering immediate document sync")

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<DocumentSyncWorkerImpl>()
                    .setConstraints(constraints)
                    .addTag("${TAG}_immediate")
                    .build()

            WorkManager.getInstance(context).enqueue(workRequest)

            Log.d(TAG, "✅ Immediate sync enqueued")
        }
    }
}

/**
 * Actual object wrapper for common interface
 * Requires context to be initialized before use
 */
actual object DocumentSyncWorker {
    private lateinit var appContext: Context

    /**
     * Initialize with application context
     * Must be called before using schedule/cancel/triggerSync
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Schedule periodic document sync
     */
    actual fun schedule() {
        if (::appContext.isInitialized) {
            DocumentSyncWorkerImpl.schedule(appContext)
        } else {
            Log.w("DocumentSyncWorker", "⚠️ Context not initialized - call initialize() first")
        }
    }

    /**
     * Cancel scheduled document sync
     */
    actual fun cancel() {
        if (::appContext.isInitialized) {
            DocumentSyncWorkerImpl.cancel(appContext)
        } else {
            Log.w("DocumentSyncWorker", "⚠️ Context not initialized - call initialize() first")
        }
    }

    /**
     * Trigger immediate sync (one-time)
     */
    actual fun triggerSync() {
        if (::appContext.isInitialized) {
            DocumentSyncWorkerImpl.syncNow(appContext)
        } else {
            Log.w("DocumentSyncWorker", "⚠️ Context not initialized - call initialize() first")
        }
    }
}
