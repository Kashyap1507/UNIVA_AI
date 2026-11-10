package com.kashyap.wizlyai.offline

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UX-017: Offline Queue Manager
 *
 * Coordinates queue operations with network detection and auto-retry logic.
 * Listens for connectivity changes and automatically processes pending operations
 * when network is restored.
 */
class OfflineQueueManager(
    private val queue: OfflineQueue,
    private val operationProcessor: OperationProcessor,
    private val coroutineScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private var processingJob: Job? = null
    private var isInitialized = false

    /**
     * Initialize queue manager (call once at app start)
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        // Start observing queue state
        coroutineScope.launch {
            queue.queueState.collect { queueState ->
                _state.value = queueState
            }
        }

        println("✅ [OfflineQueue] Manager initialized")
    }

    /**
     * Enqueue operation for processing
     */
    suspend fun enqueue(operation: QueuedOperation) {
        println("📋 [OfflineQueue] Enqueuing operation: ${operation.type} (id: ${operation.id})")
        queue.enqueue(operation)
    }

    /**
     * Manually trigger queue processing (e.g., user clicks retry)
     */
    suspend fun processQueue() {
        if (processingJob?.isActive == true) {
            println("⚠️ [OfflineQueue] Already processing queue")
            return
        }

        processingJob =
            coroutineScope.launch {
                try {
                    println("🔄 [OfflineQueue] Processing queue...")
                    val successCount = processAllOperations()
                    println("✅ [OfflineQueue] Processed $successCount operations successfully")
                } catch (e: Exception) {
                    println("❌ [OfflineQueue] Error processing queue: ${e.message}")
                    _state.value = _state.value.copy(lastError = e.message)
                }
            }
    }

    /**
     * Auto-process queue when connectivity is restored
     * Called by network detector when online status changes to true
     */
    suspend fun onConnectivityRestored() {
        println("📶 [OfflineQueue] Connectivity restored - processing queue")
        processQueue()
    }

    /**
     * Cancel specific operation
     */
    suspend fun cancelOperation(operationId: String) {
        println("🚫 [OfflineQueue] Cancelling operation: $operationId")
        queue.cancel(operationId)
    }

    /**
     * Get current pending operations for UI display
     */
    suspend fun getPendingOperations(): List<QueuedOperation> {
        return queue.getPendingOperations()
    }

    // Private helper methods

    private suspend fun processAllOperations(): Int {
        val operations = queue.getPendingOperations()
        var successCount = 0

        for (operation in operations) {
            if (operation.canRetry()) {
                val success = processOperation(operation)
                if (success) {
                    successCount++
                    queue.remove(operation.id)
                } else {
                    // Update operation with retry count
                    val updatedOp = operation.withRetry()
                    if (!updatedOp.canRetry()) {
                        println("❌ [OfflineQueue] Max retries reached for ${operation.type}")
                        queue.remove(operation.id) // Remove failed operation
                    }
                }
            }
        }

        return successCount
    }

    private suspend fun processOperation(operation: QueuedOperation): Boolean {
        return try {
            println("🔄 [OfflineQueue] Processing: ${operation.type} (attempt ${operation.retryCount + 1}/${operation.maxRetries})")

            val result = operationProcessor.process(operation)

            if (result.isSuccess) {
                println("✅ [OfflineQueue] Success: ${operation.type}")
                _state.value = _state.value.copy(lastProcessedOperation = operation.markSucceeded())
                true
            } else {
                println("❌ [OfflineQueue] Failed: ${operation.type} - ${result.error}")
                _state.value = _state.value.copy(lastError = result.error)
                false
            }
        } catch (e: Exception) {
            println("❌ [OfflineQueue] Exception: ${operation.type} - ${e.message}")
            _state.value = _state.value.copy(lastError = e.message)
            false
        }
    }
}

/**
 * Interface for processing queued operations
 * Implementations provide actual API calls for each operation type
 */
interface OperationProcessor {
    /**
     * Process a queued operation
     * Returns result indicating success/failure
     */
    suspend fun process(operation: QueuedOperation): ProcessingResult
}

/**
 * Result of processing an operation
 */
data class ProcessingResult(
    val isSuccess: Boolean,
    val error: String? = null,
)
