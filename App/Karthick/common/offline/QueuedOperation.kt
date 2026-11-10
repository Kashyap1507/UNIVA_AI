package com.kashyap.wizlyai.offline

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * UX-017: Queued Operation Data Models
 *
 * Represents an operation that failed (e.g., due to network error)
 * and needs to be retried when connectivity is restored.
 */

@Serializable
data class QueuedOperation(
    val id: String, // Unique operation ID
    val type: OperationType,
    val payload: String, // JSON-serialized operation data
    val timestamp: Long, // When operation was queued (epoch millis)
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val lastAttempt: Long? = null, // Last retry attempt timestamp
    val status: OperationStatus = OperationStatus.PENDING,
) {
    /**
     * Check if operation can be retried based on retry policy
     */
    fun canRetry(): Boolean {
        return retryCount < maxRetries && status != OperationStatus.SUCCEEDED
    }

    /**
     * Get next retry delay in milliseconds using exponential backoff
     * Formula: min(initialDelay * 2^retryCount, maxDelay)
     */
    fun getNextRetryDelay(): Long {
        val initialDelay = 5000L // 5 seconds
        val maxDelay = 300000L // 5 minutes
        val exponentialDelay = initialDelay * (1 shl retryCount) // 2^retryCount
        return minOf(exponentialDelay, maxDelay)
    }

    /**
     * Create a copy with incremented retry count
     */
    fun withRetry(): QueuedOperation {
        return copy(
            retryCount = retryCount + 1,
            lastAttempt = Clock.System.now().toEpochMilliseconds(),
            status = OperationStatus.RETRYING,
        )
    }

    /**
     * Mark operation as succeeded
     */
    fun markSucceeded(): QueuedOperation {
        return copy(status = OperationStatus.SUCCEEDED)
    }

    /**
     * Mark operation as failed
     */
    fun markFailed(reason: String): QueuedOperation {
        return copy(
            status = OperationStatus.FAILED,
            lastAttempt = Clock.System.now().toEpochMilliseconds(),
        )
    }
}

/**
 * Type of queued operation
 */
@Serializable
enum class OperationType {
    UPLOAD_DOCUMENT, // Upload a scan of the student's work
    UPDATE_SETTINGS, // Update user settings
}

@Serializable
enum class OperationStatus {
    PENDING, // Waiting to be processed
    RETRYING, // Currently being retried
    SUCCEEDED, // Successfully completed
    FAILED, // Failed after max retries
    CANCELLED, // Manually cancelled by user
}

@Serializable
data class UploadDocumentPayload(
    val userId: String,
    val documentId: String? = null,
    val imagePath: String,
    val documentType: String,
    val title: String? = null,
)

/**
 * Payload for UPDATE_SETTINGS operations
 */
@Serializable
data class UpdateSettingsPayload(
    val userId: String? = null,
    val settings: Map<String, String>? = null,
    val settingType: String? = null,
)
