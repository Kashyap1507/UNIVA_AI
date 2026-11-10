package com.kashyap.wizlyai.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * UX-017: iOS Offline Queue Implementation
 *
 * TODO: Complete iOS UserDefaults persistence implementation
 * Currently using in-memory storage only. Operations will not survive app restarts.
 * For full persistence, requires proper C-interop setup with Swift UserDefaults bridge.
 */
class IOSOfflineQueue : OfflineQueue {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    // TODO: Replace with UserDefaults persistence
    private val inMemoryOperations = mutableListOf<QueuedOperation>()

    private val _queueState = MutableStateFlow(QueueState())
    override val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    init {
        updateQueueState()
    }

    override suspend fun enqueue(operation: QueuedOperation) {
        val operations = loadOperations().toMutableList()
        operations.add(operation)
        saveOperations(operations)
        updateQueueState()

        println("📋 [IOSOfflineQueue] Enqueued ${operation.type} (id: ${operation.id})")
    }

    override suspend fun getPendingOperations(): List<QueuedOperation> {
        return loadOperations().filter {
            it.status == OperationStatus.PENDING || it.status == OperationStatus.RETRYING
        }
    }

    override suspend fun processNext(): Boolean {
        val pending = getPendingOperations()
        if (pending.isEmpty()) return false

        _queueState.value = _queueState.value.copy(isProcessing = true)
        return true
    }

    override suspend fun processAll(): Int {
        val pending = getPendingOperations()
        _queueState.value = _queueState.value.copy(isProcessing = true)
        return pending.size
    }

    override suspend fun remove(operationId: String) {
        val operations = loadOperations().toMutableList()
        val removed = operations.removeAll { it.id == operationId }

        if (removed) {
            saveOperations(operations)
            updateQueueState()
            println("🗑️ [IOSOfflineQueue] Removed operation: $operationId")
        }
    }

    override suspend fun cancel(operationId: String) {
        val operations = loadOperations().toMutableList()
        val index = operations.indexOfFirst { it.id == operationId }

        if (index != -1) {
            operations[index] = operations[index].copy(status = OperationStatus.CANCELLED)
            saveOperations(operations)
            updateQueueState()
            println("🚫 [IOSOfflineQueue] Cancelled operation: $operationId")
        }
    }

    override suspend fun clearAll() {
        // TODO: Clear from UserDefaults when C-interop is set up
        inMemoryOperations.clear()
        _queueState.value = QueueState()
        println("🗑️ [IOSOfflineQueue] Cleared all operations")
    }

    override suspend fun getOperation(operationId: String): QueuedOperation? {
        return loadOperations().firstOrNull { it.id == operationId }
    }

    // Private helper methods

    private fun loadOperations(): List<QueuedOperation> {
        // TODO: Load from UserDefaults when C-interop is set up
        return inMemoryOperations.toList()
    }

    private fun saveOperations(operations: List<QueuedOperation>) {
        // TODO: Save to UserDefaults when C-interop is set up
        inMemoryOperations.clear()
        inMemoryOperations.addAll(operations)
    }

    private fun updateQueueState() {
        val operations = loadOperations()
        val pendingCount =
            operations.count {
                it.status == OperationStatus.PENDING || it.status == OperationStatus.RETRYING
            }

        _queueState.value =
            _queueState.value.copy(
                pendingCount = pendingCount,
                isProcessing = false,
            )
    }
}
