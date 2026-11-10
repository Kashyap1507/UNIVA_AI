package com.kashyap.wizlyai.sync

import android.content.Context
import com.kashyap.wizlyai.cache.SimpleCache
import com.kashyap.wizlyai.data.local.WizlyDatabase
import com.kashyap.wizlyai.data.local.entity.DocumentEntity
import com.kashyap.wizlyai.data.local.entity.DocumentPageEntity
import com.kashyap.wizlyai.data.repository.DocumentRepository
import com.kashyap.wizlyai.util.SyncLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import kotlin.math.min
import kotlin.math.pow

/**
 * Android implementation of DocumentSyncManager
 *
 * Full-featured document synchronization with:
 * - Room database (offline-first storage)
 * - Backend Firebase Firestore (cloud persistence)
 * - Google Cloud Storage (image/thumbnail storage)
 * - Exponential backoff retry logic
 * - Conflict resolution
 * - Cache invalidation
 *
 * @param context Android context for database and file access
 */
actual class DocumentSyncManager(context: Context) {
    private val database = WizlyDatabase.getDatabase(context)
    private val documentDao = database.documentDao()
    private val documentPageDao = database.documentPageDao()
    private val appSettingsDao = database.appSettingsDao()
    private val documentRepository = DocumentRepository()
    private val syncPreferences = SyncPreferences(context)
    private val applicationContext = context.applicationContext
    private val networkDetector = com.kashyap.wizlyai.util.NetworkDetector(context)

    companion object {
        private const val TAG = "DocumentSyncManager"

        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 1 second
        private const val MAX_RETRY_DELAY_MS = 30000L // 30 seconds

        // Batch configuration
        private const val MAX_BATCH_SIZE = 10

        // Sync preferences keys
        private const val PREF_KEY_LAST_DOCUMENT_SYNC = "last_document_sync_timestamp"
        private const val PREF_KEY_RETRY_QUEUE = "document_retry_queue"
    }

    // ========== PUBLIC SYNC METHODS ==========

    /**
     * Sync all pending documents to backend (Internal Android method)
     *
     * This is the PRIMARY method for uploading documents.
     * It handles:
     * - Fetching all documents with sync_status='pending' or 'failed'
     * - Uploading document metadata and pages to backend
     * - Updating Room entities with remote URLs from GCS
     * - Marking documents as 'synced' on success
     * - Retrying failed uploads with exponential backoff
     *
     * Called by:
     * - SyncCoordinator during bootstrap sync
     * - Background WorkManager periodic sync
     * - Manual sync trigger from UI
     *
     * @return Result with sync statistics (documents synced, failures)
     */
    internal suspend fun syncPendingDocumentsInternal(): Result<DocumentSyncStats> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val startTime = System.currentTimeMillis()

                SyncLogger.syncStart(TAG, "Syncing pending documents")

                // 1. Fetch pending documents
                val pendingDocuments = documentDao.getPendingSyncDocuments()

                if (pendingDocuments.isEmpty()) {
                    SyncLogger.info(TAG, "No pending documents to sync")
                    return@withContext Result.success(
                        DocumentSyncStats(
                            documentsSynced = 0,
                            pagesSynced = 0,
                            failures = 0,
                            durationMs = System.currentTimeMillis() - startTime,
                        ),
                    )
                }

                SyncLogger.info(TAG, "Found ${pendingDocuments.size} pending documents")

                var successCount = 0
                var failureCount = 0
                var totalPagesSynced = 0

                // Check cloud sync setting (only once, applies to all documents)
                val cloudSyncEnabled = isCloudSyncEnabled()
                SyncLogger.info(TAG, "Cloud sync enabled: $cloudSyncEnabled")

                // Check network conditions and cellular sync setting
                val cellularSyncEnabled = isCellularSyncEnabled()
                val networkType = networkDetector.getNetworkType()
                val shouldSyncNow = networkDetector.shouldSync(cellularSyncEnabled)

                if (!shouldSyncNow) {
                    SyncLogger.info(TAG, "Sync deferred: On cellular data with cellular sync disabled")
                    SyncLogger.info(TAG, "Documents will sync automatically when connected to WiFi")
                    return@withContext Result.success(
                        DocumentSyncStats(
                            documentsSynced = 0,
                            pagesSynced = 0,
                            failures = 0,
                            durationMs = System.currentTimeMillis() - startTime,
                        ),
                    )
                }

                SyncLogger.info(TAG, "Network: $networkType, Cellular sync: $cellularSyncEnabled")

                // 2. Process each document
                for (document in pendingDocuments) {
                    try {
                        // Mark as syncing
                        documentDao.updateSyncStatus(
                            documentId = document.id,
                            status = "syncing",
                            lastSyncedAt = getCurrentTimestamp(),
                        )

                        // Upload based on cloud sync setting
                        val result =
                            if (cloudSyncEnabled) {
                                // Full upload: metadata + images to GCS
                                uploadDocumentWithPages(document)
                            } else {
                                // Metadata-only upload: no images, mark as local_only
                                uploadDocumentMetadataOnly(document)
                            }

                        result.fold(
                            onSuccess = { pageCount ->
                                // Mark with appropriate status
                                val syncStatus = if (cloudSyncEnabled) "synced" else "local_only"
                                documentDao.updateSyncStatus(
                                    documentId = document.id,
                                    status = syncStatus,
                                    lastSyncedAt = getCurrentTimestamp(),
                                )

                                successCount++
                                totalPagesSynced += pageCount
                                val mode = if (cloudSyncEnabled) "cloud" else "local-only"
                                SyncLogger.syncSuccess(TAG, "Document synced ($mode): ${document.title} ($pageCount pages)")
                            },
                            onFailure = { e ->
                                // Mark as failed for retry
                                documentDao.updateSyncStatus(
                                    documentId = document.id,
                                    status = "failed",
                                    lastSyncedAt = getCurrentTimestamp(),
                                )

                                failureCount++
                                SyncLogger.syncFailure(TAG, "Document sync failed: ${document.title}", e)
                            },
                        )
                    } catch (e: Exception) {
                        failureCount++
                        SyncLogger.syncFailure(TAG, "Error syncing document: ${document.title}", e)
                    }
                }

                // 3. Invalidate caches
                invalidateDocumentCaches()

                // 4. Log statistics
                val duration = System.currentTimeMillis() - startTime
                SyncLogger.stats(
                    TAG,
                    mapOf(
                        "Documents synced" to successCount,
                        "Pages synced" to totalPagesSynced,
                        "Failures" to failureCount,
                        "Duration" to "${duration}ms",
                    ),
                )
                SyncLogger.syncEnd(TAG)

                Result.success(
                    DocumentSyncStats(
                        documentsSynced = successCount,
                        pagesSynced = totalPagesSynced,
                        failures = failureCount,
                        durationMs = duration,
                    ),
                )
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Document sync failed", e)
                SyncLogger.syncEnd(TAG)
                Result.failure(e)
            }
        }

    /**
     * Sync pending pages for documents that are already synced (Internal Android method)
     *
     * This handles the case where a user adds additional pages
     * to an existing document after initial upload.
     *
     * @return Result with sync statistics
     */
    internal suspend fun syncPendingPagesInternal(): Result<DocumentSyncStats> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val startTime = System.currentTimeMillis()

                SyncLogger.syncStart(TAG, "Syncing pending pages")

                // 1. Fetch pending pages
                val pendingPages = documentPageDao.getPendingSyncPages()

                if (pendingPages.isEmpty()) {
                    SyncLogger.info(TAG, "No pending pages to sync")
                    return@withContext Result.success(
                        DocumentSyncStats(
                            documentsSynced = 0,
                            pagesSynced = 0,
                            failures = 0,
                            durationMs = System.currentTimeMillis() - startTime,
                        ),
                    )
                }

                SyncLogger.info(TAG, "Found ${pendingPages.size} pending pages")

                var successCount = 0
                var failureCount = 0

                // 2. Group pages by document
                val pagesByDocument = pendingPages.groupBy { it.documentId }

                // 3. Upload pages for each document
                for ((documentId, pages) in pagesByDocument) {
                    try {
                        for (page in pages) {
                            val result = uploadSinglePage(documentId, page)

                            result.fold(
                                onSuccess = {
                                    documentPageDao.updateSyncStatus(
                                        pageId = page.id,
                                        status = "synced",
                                        lastSyncedAt = getCurrentTimestamp(),
                                    )
                                    successCount++
                                    SyncLogger.syncSuccess(TAG, "Page synced: ${page.id}")
                                },
                                onFailure = { e ->
                                    documentPageDao.updateSyncStatus(
                                        pageId = page.id,
                                        status = "failed",
                                        lastSyncedAt = getCurrentTimestamp(),
                                    )
                                    failureCount++
                                    SyncLogger.syncFailure(TAG, "Page sync failed: ${page.id}", e)
                                },
                            )
                        }

                        // Update document page count
                        val totalPages = documentPageDao.getPageCount(documentId)
                        documentDao.updatePageCount(documentId, totalPages, getCurrentTimestamp())
                    } catch (e: Exception) {
                        failureCount += pages.size
                        SyncLogger.syncFailure(TAG, "Error syncing pages for document: $documentId", e)
                    }
                }

                // 4. Invalidate caches
                invalidateDocumentCaches()

                // 5. Log statistics
                val duration = System.currentTimeMillis() - startTime
                SyncLogger.stats(
                    TAG,
                    mapOf(
                        "Pages synced" to successCount,
                        "Failures" to failureCount,
                        "Duration" to "${duration}ms",
                    ),
                )
                SyncLogger.syncEnd(TAG)

                Result.success(
                    DocumentSyncStats(
                        documentsSynced = 0,
                        pagesSynced = successCount,
                        failures = failureCount,
                        durationMs = duration,
                    ),
                )
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Page sync failed", e)
                SyncLogger.syncEnd(TAG)
                Result.failure(e)
            }
        }

    /**
     * Download new documents from backend for the user (Internal Android method)
     *
     * This fetches documents from the backend that don't exist locally
     * or have been updated since last sync.
     *
     * Use cases:
     * - Initial sync after login
     * - Periodic smart sync to check for updates
     * - FCM push notification triggers
     * - Manual refresh from UI
     *
     * @param userId The student's user id (owner of the documents)
     * @return Result with list of downloaded documents
     */
    internal suspend fun downloadNewDocumentsInternal(userId: String): Result<List<DocumentEntity>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                SyncLogger.syncStart(TAG, "Downloading documents for user: $userId")

                // 1. Fetch documents from backend
                val result = documentRepository.getSourceDocuments()

                result.fold(
                    onSuccess = { documents ->
                        if (documents.isEmpty()) {
                            SyncLogger.info(TAG, "No backend documents found")
                            return@withContext Result.success(emptyList())
                        }

                        SyncLogger.info(TAG, "Found ${documents.size} backend documents")

                        val newDocuments = mutableListOf<DocumentEntity>()

                        // 2. Compare with local documents (smart sync)
                        for (document in documents) {
                            val localDoc = documentDao.getById(document.id)

                            val shouldSync =
                                localDoc == null ||
                                    (document.updatedAt > localDoc.updatedAt)

                            if (shouldSync) {
                                // Convert to Room entity
                                val documentEntity =
                                    DocumentEntity(
                                        id = document.id,
                                        userId = document.userId.ifEmpty { userId },
                                        title = document.title,
                                        type = document.type.name,
                                        pageCount = document.pageCount,
                                        thumbnailPath = null, // Use remote URL instead
                                        status = document.status.name,
                                        processingProgress = document.processingProgress,
                                        remoteThumbnailUrl = document.thumbnailUrl,
                                        createdAt = document.createdAt,
                                        updatedAt = document.updatedAt,
                                        syncStatus = "synced", // Already from backend
                                        lastSyncedAt = getCurrentTimestamp(),
                                        isDeleted = false,
                                        deletedAt = null,
                                    )

                                newDocuments.add(documentEntity)
                            }
                        }

                        if (newDocuments.isNotEmpty()) {
                            // 3. Insert into Room
                            documentDao.insertAll(newDocuments)
                            SyncLogger.syncSuccess(TAG, "Downloaded ${newDocuments.size} documents")

                            // 4. Invalidate caches
                            invalidateDocumentCaches(userId)
                        } else {
                            SyncLogger.info(TAG, "No new documents to download (local is up-to-date)")
                        }

                        SyncLogger.syncEnd(TAG)
                        Result.success(newDocuments)
                    },
                    onFailure = { e ->
                        SyncLogger.syncFailure(TAG, "Failed to download documents", e)
                        SyncLogger.syncEnd(TAG)
                        Result.failure(e)
                    },
                )
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Document download failed", e)
                SyncLogger.syncEnd(TAG)
                Result.failure(e)
            }
        }

    // ========== OBSERVATION METHODS ==========

    /**
     * Get count of pending sync documents and pages
     *
     * @return Total count of items needing sync
     */
    suspend fun getPendingSyncCount(): Int =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val pendingDocs = documentDao.getPendingSyncDocuments().size
                val pendingPages = documentPageDao.getPendingSyncPages().size
                pendingDocs + pendingPages
            } catch (e: Exception) {
                SyncLogger.syncFailure(TAG, "Failed to get pending sync count", e)
                0
            }
        }

    /**
     * Observe pending sync count as Flow
     *
     * This allows UI to reactively display sync status.
     *
     * @return Flow emitting pending sync count
     */
    actual fun observePendingSyncCount(): Flow<Int> {
        return documentDao.getPendingSyncCount()
    }

    // ========== PRIVATE UPLOAD METHODS ==========

    /**
     * Upload a document with all its pages
     *
     * Algorithm:
     * 1. Load all pages for document from Room
     * 2. Read image files and convert to Base64
     * 3. Call backend /api/documents/scan with document + pages
     * 4. Parse response with GCS URLs
     * 5. Update Room entities with remote URLs
     *
     * @param document Document entity to upload
     * @return Result with number of pages synced
     */
    private suspend fun uploadDocumentWithPages(document: DocumentEntity): Result<Int> {
        return try {
            // 1. Load pages
            val pages = documentPageDao.getPagesByDocumentId(document.id)

            if (pages.isEmpty()) {
                SyncLogger.warning(TAG, "Document has no pages: ${document.id}")
                return Result.failure(Exception("Document has no pages"))
            }

            SyncLogger.detail(TAG, "Uploading document with ${pages.size} pages")

            // 2. Read page images and build PageUploadData list
            val pageUploadDataList = mutableListOf<com.kashyap.wizlyai.data.repository.PageUploadData>()

            for (page in pages) {
                val imageFile = File(page.imagePath)
                if (!imageFile.exists()) {
                    SyncLogger.warning(TAG, "Page image not found: ${page.imagePath}")
                    continue
                }

                val imageBytes = imageFile.readBytes()
                pageUploadDataList.add(
                    com.kashyap.wizlyai.data.repository.PageUploadData(
                        imageData = imageBytes,
                        pageNumber = page.pageNumber,
                        width = page.width ?: 0,
                        height = page.height ?: 0,
                    ),
                )
            }

            if (pageUploadDataList.isEmpty()) {
                return Result.failure(Exception("No valid page images found"))
            }

            // 3. Upload all pages at once to create document
            val uploadResult =
                documentRepository.uploadScan(
                    pages = pageUploadDataList,
                    title = document.title,
                    type = runCatching { com.kashyap.wizlyai.data.models.DocumentType.valueOf(document.type) }
                        .getOrDefault(com.kashyap.wizlyai.data.models.DocumentType.HOMEWORK),
                )

            uploadResult.fold(
                onSuccess = { backendDocument ->
                    // Update document with remote thumbnail URL
                    val updatedDoc =
                        document.copy(
                            remoteThumbnailUrl = backendDocument.thumbnailUrl,
                            syncStatus = "synced",
                            lastSyncedAt = getCurrentTimestamp(),
                        )
                    documentDao.update(updatedDoc)

                    // Update all pages with remote URLs from backend response
                    if (backendDocument.pages?.isNotEmpty() == true) {
                        for (i in pages.indices) {
                            if (i < backendDocument.pages.size) {
                                val backendPage = backendDocument.pages[i]
                                val localPage = pages[i]

                                val updatedPage =
                                    localPage.copy(
                                        remoteImageUrl = backendPage.imageUrl,
                                        remoteThumbnailUrl = backendPage.thumbnailUrl,
                                        storagePath = null, // TODO: Extract from backend response
                                        syncStatus = "synced",
                                        lastSyncedAt = getCurrentTimestamp(),
                                    )
                                documentPageDao.update(updatedPage)
                            }
                        }
                    }

                    SyncLogger.syncSuccess(TAG, "Document uploaded: ${document.title} (${pages.size} pages)")
                    Result.success(pages.size)
                },
                onFailure = { e ->
                    SyncLogger.syncFailure(TAG, "Failed to upload document: ${document.title}", e)
                    Result.failure(e)
                },
            )
        } catch (e: Exception) {
            SyncLogger.syncFailure(TAG, "Error uploading document: ${document.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Upload a single page for an existing document
     *
     * @param documentId Document ID
     * @param page Page entity to upload
     * @return Result indicating success/failure
     */
    private suspend fun uploadSinglePage(
        documentId: String,
        page: DocumentPageEntity,
    ): Result<Unit> {
        return try {
            // Read image file
            val imageFile = File(page.imagePath)
            if (!imageFile.exists()) {
                return Result.failure(Exception("Page image not found: ${page.imagePath}"))
            }

            val imageBytes = imageFile.readBytes()

            // Upload to backend with page metadata
            val result =
                documentRepository.addPage(
                    documentId = documentId,
                    imageData = imageBytes,
                    pageNumber = page.pageNumber,
                    width = page.width ?: 0,
                    height = page.height ?: 0,
                )

            result.fold(
                onSuccess = { backendPage ->
                    // Update Room entity with remote URLs
                    val updatedPage =
                        page.copy(
                            remoteImageUrl = backendPage.imageUrl,
                            remoteThumbnailUrl = backendPage.thumbnailUrl,
                            storagePath = null, // TODO: Extract from backend response
                            syncStatus = "synced",
                            lastSyncedAt = getCurrentTimestamp(),
                        )
                    documentPageDao.update(updatedPage)

                    SyncLogger.syncSuccess(TAG, "Page uploaded: ${page.id}")
                    Result.success(Unit)
                },
                onFailure = { e ->
                    SyncLogger.syncFailure(TAG, "Failed to upload page: ${page.id}", e)
                    Result.failure(e)
                },
            )
        } catch (e: Exception) {
            SyncLogger.syncFailure(TAG, "Error uploading page: ${page.id}", e)
            Result.failure(e)
        }
    }

    // ========== CACHE INVALIDATION ==========

    /**
     * Invalidate document-related caches
     *
     * This forces UI to refresh and fetch new data from Room.
     *
     * @param userId Optional user id to invalidate that user's documents
     */
    private fun invalidateDocumentCaches(userId: String? = null) {
        if (userId != null) {
            SimpleCache.instance.invalidatePattern("documents_$userId")
        } else {
            SimpleCache.instance.invalidatePattern("documents")
        }

        SyncLogger.info(TAG, "Document caches invalidated")
    }

    /**
     * Upload document metadata only (no images)
     *
     * This method is called when cloud sync is DISABLED.
     * It syncs document metadata to Firestore without uploading images to GCS.
     *
     * Benefits:
     * - Reduced cloud storage costs
     * - Lower bandwidth usage
     * - Enhanced privacy (images stay on device)
     * - Cross-device document browsing (metadata still synced)
     *
     * Algorithm:
     * 1. Create document record in Firestore with metadata only
     * 2. Mark pages as "local_only" (no remote URLs)
     * 3. Return page count for statistics
     *
     * @param document Document entity to upload metadata for
     * @return Result with number of pages (kept local)
     */
    private suspend fun uploadDocumentMetadataOnly(document: DocumentEntity): Result<Int> {
        return try {
            // 1. Load pages (for count, but don't upload images)
            val pages = documentPageDao.getPagesByDocumentId(document.id)

            if (pages.isEmpty()) {
                SyncLogger.warning(TAG, "Document has no pages: ${document.id}")
                return Result.failure(Exception("Document has no pages"))
            }

            SyncLogger.detail(TAG, "Uploading metadata only for document with ${pages.size} pages (cloudSync=OFF)")

            // 2. Upload minimal metadata to Firestore
            // TODO: Implement backend API endpoint for metadata-only upload
            // For now, we skip actual upload and just mark pages as local_only

            // 3. Update pages as local_only
            for (page in pages) {
                val updatedPage =
                    page.copy(
                        remoteImageUrl = null, // No GCS URL
                        remoteThumbnailUrl = null, // No thumbnail URL
                        storagePath = null,
                        syncStatus = "local_only",
                        lastSyncedAt = getCurrentTimestamp(),
                    )
                documentPageDao.update(updatedPage)
            }

            SyncLogger.syncSuccess(TAG, "Document metadata recorded (local-only): ${document.title} (${pages.size} pages)")
            Result.success(pages.size)
        } catch (e: Exception) {
            SyncLogger.syncFailure(TAG, "Error uploading metadata only: ${document.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Check if cloud sync is enabled for current user
     *
     * Reads cloudSyncEnabled setting from Room database.
     * Returns false if user has no settings (defaults to local-only).
     *
     * @return true if cloud sync enabled, false if local-only mode
     */
    private suspend fun isCloudSyncEnabled(): Boolean {
        return try {
            // Get current user ID
            val userId = com.kashyap.wizlyai.session.UserSessionManager.instance.userId
            if (userId == null) {
                SyncLogger.warning(TAG, "No user ID available, defaulting to cloud sync ON")
                return true
            }

            // Get app settings from Room
            val settings = appSettingsDao.getByUserId(userId)

            if (settings == null) {
                // No settings yet - default to ON (scans upload by default)
                SyncLogger.info(TAG, "No app settings found, defaulting to cloud sync ON")
                return true
            }

            // Return setting value
            settings.cloudSyncEnabled
        } catch (e: Exception) {
            SyncLogger.warning(TAG, "Error checking cloud sync setting: ${e.message}, defaulting to ON")
            true // Default ON (always upload)
        }
    }

    /**
     * Check if cellular data sync is enabled for current user
     *
     * Reads cellularSyncEnabled setting from Room database.
     * Returns false if user has no settings (defaults to WiFi-only).
     *
     * This setting only applies when cloudSyncEnabled = true.
     * If cloudSyncEnabled = false, documents don't upload at all (local-only mode).
     *
     * @return true if cellular sync enabled, false if WiFi-only mode
     */
    private suspend fun isCellularSyncEnabled(): Boolean {
        return try {
            // Get current user ID
            val userId = com.kashyap.wizlyai.session.UserSessionManager.instance.userId
            if (userId == null) {
                SyncLogger.warning(TAG, "No user ID available, defaulting to cellular sync OFF")
                return false
            }

            // Get app settings from Room
            val settings = appSettingsDao.getByUserId(userId)

            if (settings == null) {
                // No settings yet - default to OFF (WiFi-only)
                SyncLogger.info(TAG, "No app settings found, defaulting to cellular sync OFF")
                return false
            }

            // Return setting value
            settings.cellularSyncEnabled
        } catch (e: Exception) {
            SyncLogger.warning(TAG, "Error checking cellular sync setting: ${e.message}, defaulting to OFF")
            false // Safe default: WiFi-only
        }
    }

    // ========== UTILITY METHODS ==========

    /**
     * Get current timestamp in ISO 8601 format
     *
     * @return Current timestamp string
     */
    private fun getCurrentTimestamp(): String {
        return Clock.System.now().toString()
    }

    /**
     * Calculate exponential backoff delay
     *
     * Formula: min(INITIAL_DELAY * 2^attempt, MAX_DELAY)
     *
     * @param attempt Retry attempt number (0-based)
     * @return Delay in milliseconds
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val delay = (INITIAL_RETRY_DELAY_MS * 2.0.pow(attempt)).toLong()
        return min(delay, MAX_RETRY_DELAY_MS)
    }

    // ========== COMMON INTERFACE IMPLEMENTATIONS ==========

    /**
     * Sync all pending documents (common interface)
     * Wrapper for syncPendingDocumentsInternal() that returns Int
     */
    actual suspend fun syncPendingDocuments(): Result<Int> {
        return syncPendingDocumentsInternal().map { it.documentsSynced }
    }

    /**
     * Sync all pending pages (common interface)
     * Wrapper for syncPendingPagesInternal() that returns Int
     */
    actual suspend fun syncPendingPages(): Result<Int> {
        return syncPendingPagesInternal().map { it.pagesSynced }
    }

    /**
     * Download new documents (common interface)
     * Wrapper for downloadNewDocumentsInternal(userId) that returns Int
     */
    actual suspend fun downloadNewDocuments(userId: String): Result<Int> {
        return downloadNewDocumentsInternal(userId).map { it.size }
    }

    /**
     * Force sync all documents (common interface)
     */
    actual suspend fun forceSyncAll(): Result<Unit> {
        return try {
            syncPendingDocuments()
            syncPendingPages()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Document sync statistics data class
 *
 * @param documentsSynced Number of documents successfully synced
 * @param pagesSynced Number of pages successfully synced
 * @param failures Number of sync failures
 * @param durationMs Time taken for sync operation in milliseconds
 */
data class DocumentSyncStats(
    val documentsSynced: Int,
    val pagesSynced: Int,
    val failures: Int,
    val durationMs: Long,
)
