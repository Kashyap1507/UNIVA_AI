package com.kashyap.wizlyai.offline

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * UX-017: Android Offline Queue Implementation
 *
 * Uses SharedPreferences for persistent queue storage.
 * Operations survive app restarts and can be processed when connectivity is restored.
 */
class AndroidOfflineQueue(private val context: Context) : OfflineQueue {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            "offline_queue",
            Context.MODE_PRIVATE,
        )

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

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

        println("📋 [AndroidOfflineQueue] Enqueued ${operation.type} (id: ${operation.id})")
    }

    override suspend fun getPendingOperations(): List<QueuedOperation> {
        return loadOperations().filter {
            it.status == OperationStatus.PENDING || it.status == OperationStatus.RETRYING
        }
    }

    override suspend fun processNext(): Boolean {
        val pending = getPendingOperations()
        if (pending.isEmpty()) return false

        val operation = pending.first()
        _queueState.value = _queueState.value.copy(isProcessing = true)

        // Actual processing is handled by OperationProcessor in OfflineQueueManager
        // This method just returns true to indicate operation is ready to process
        return true
    }

    override suspend fun processAll(): Int {
        val pending = getPendingOperations()
        _queueState.value = _queueState.value.copy(isProcessing = true)

        // Actual processing is handled by OfflineQueueManager
        // Return count of pending operations
        return pending.size
    }

    override suspend fun remove(operationId: String) {
        val operations = loadOperations().toMutableList()
        val removed = operations.removeAll { it.id == operationId }

        if (removed) {
            saveOperations(operations)
            updateQueueState()
            println("🗑️ [AndroidOfflineQueue] Removed operation: $operationId")
        }
    }

    override suspend fun cancel(operationId: String) {
        val operations = loadOperations().toMutableList()
        val index = operations.indexOfFirst { it.id == operationId }

        if (index != -1) {
            operations[index] = operations[index].copy(status = OperationStatus.CANCELLED)
            saveOperations(operations)
            updateQueueState()
            println("🚫 [AndroidOfflineQueue] Cancelled operation: $operationId")
        }
    }

    override suspend fun clearAll() {
        prefs.edit().clear().apply()
        _queueState.value = QueueState()
        println("🗑️ [AndroidOfflineQueue] Cleared all operations")
    }

    override suspend fun getOperation(operationId: String): QueuedOperation? {
        return loadOperations().firstOrNull { it.id == operationId }
    }

    // Private helper methods

    private fun loadOperations(): List<QueuedOperation> {
        val operationsJson = prefs.getString(KEY_OPERATIONS, null) ?: return emptyList()

        return try {
            json.decodeFromString<List<QueuedOperation>>(operationsJson)
        } catch (e: Exception) {
            println("❌ [AndroidOfflineQueue] Error loading operations: ${e.message}")
            emptyList()
        }
    }

    private fun saveOperations(operations: List<QueuedOperation>) {
        try {
            val operationsJson = json.encodeToString(operations)
            prefs.edit().putString(KEY_OPERATIONS, operationsJson).apply()
        } catch (e: Exception) {
            println("❌ [AndroidOfflineQueue] Error saving operations: ${e.message}")
        }
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

    companion object {
        private const val KEY_OPERATIONS = "operations"
    }
}
