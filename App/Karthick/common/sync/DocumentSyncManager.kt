package com.kashyap.wizlyai.sync

import kotlinx.coroutines.flow.Flow

/**
 * Document Sync Manager for bidirectional document synchronization
 *
 * Handles synchronization of documents and pages between local storage and backend
 *
 * Platform-specific implementations:
 * - Android: Full implementation with Room, WorkManager, exponential backoff
 * - iOS: Simplified wrapper bridging to IOSBackgroundSyncHandler.swift
 */
expect class DocumentSyncManager {
    /**
     * Sync all pending documents to backend
     * Uploads document metadata and pages
     *
     * @return Result with number of documents synced
     */
    suspend fun syncPendingDocuments(): Result<Int>

    /**
     * Sync all pending pages to backend
     * Handles page uploads for existing documents
     *
     * @return Result with number of pages synced
     */
    suspend fun syncPendingPages(): Result<Int>

    /**
     * Download new documents from backend for the user
     *
     * @param userId User ID to download documents for
     * @return Result with number of documents downloaded
     */
    suspend fun downloadNewDocuments(userId: String): Result<Int>

    /**
     * Observe pending sync count
     * Used for showing sync badges in UI
     *
     * @return Flow of pending document count
     */
    fun observePendingSyncCount(): Flow<Int>

    /**
     * Force sync all documents (ignoring sync preferences)
     *
     * @return Result indicating success/failure
     */
    suspend fun forceSyncAll(): Result<Unit>
}
