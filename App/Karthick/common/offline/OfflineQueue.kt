package com.kashyap.wizlyai.offline

import kotlinx.coroutines.flow.StateFlow

/**
 * UX-017: Offline Queue Interface
 *
 * Platform-agnostic interface for queue operations.
 * Implementations provide platform-specific persistence (SharedPreferences/UserDefaults).
 */
interface OfflineQueue {
    /**
     * Current queue state (for UI observation)
     */
    val queueState: StateFlow<QueueState>

    /**
     * Enqueue an operation for later processing
     */
    suspend fun enqueue(operation: QueuedOperation)

    /**
     * Get all pending operations
     */
    suspend fun getPendingOperations(): List<QueuedOperation>

    /**
     * Process next operation in queue
     * Returns true if operation succeeded, false otherwise
     */
    suspend fun processNext(): Boolean

    /**
     * Process all pending operations
     * Returns number of successful operations
     */
    suspend fun processAll(): Int

    /**
     * Remove operation from queue (after success or cancellation)
     */
    suspend fun remove(operationId: String)

    /**
     * Cancel operation (mark as cancelled, don't retry)
     */
    suspend fun cancel(operationId: String)

    /**
     * Clear all operations (use with caution!)
     */
    suspend fun clearAll()

    /**
     * Get operation by ID
     */
    suspend fun getOperation(operationId: String): QueuedOperation?
}

/**
 * Queue state for UI observation
 */
data class QueueState(
    val pendingCount: Int = 0,
    val isProcessing: Boolean = false,
    val lastProcessedOperation: QueuedOperation? = null,
    val lastError: String? = null,
)
