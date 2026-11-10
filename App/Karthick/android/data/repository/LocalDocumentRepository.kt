package com.kashyap.wizlyai.data.repository

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import com.kashyap.wizlyai.data.local.FileStorageManager
import com.kashyap.wizlyai.data.local.SavedImagePaths
import com.kashyap.wizlyai.data.local.WizlyDatabase
import com.kashyap.wizlyai.data.local.entity.DocumentEntity
import com.kashyap.wizlyai.data.local.entity.DocumentPageEntity
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentPage
import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.util.ImageCompressor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Android implementation of LocalDocumentRepository
 * Uses Room database with full Paging support and sync queue management
 */
actual class LocalDocumentRepository(context: Context) {
    private val database: WizlyDatabase
    private val documentDao: com.kashyap.wizlyai.data.local.dao.DocumentDao
    private val documentPageDao: com.kashyap.wizlyai.data.local.dao.DocumentPageDao

    private val imageCompressor: ImageCompressor
    private val fileStorageManager: FileStorageManager

    init {
        Log.d("LocalDocumentRepository", "🗄️ Initializing LocalDocumentRepository")
        database = WizlyDatabase.getDatabase(context)
        Log.d("LocalDocumentRepository", "✅ Database instance obtained")

        documentDao = database.documentDao()
        documentPageDao = database.documentPageDao()
        Log.d("LocalDocumentRepository", "✅ All DAOs initialized")

        imageCompressor = ImageCompressor(context)
        fileStorageManager = FileStorageManager(context, imageCompressor)

        // Force database creation by executing a simple query
        // Room uses lazy initialization, so database isn't created until first access
        try {
            database.openHelper.writableDatabase
            Log.d("LocalDocumentRepository", "✅ Database file created and opened")
        } catch (e: Exception) {
            Log.e("LocalDocumentRepository", "❌ Error creating database", e)
        }

        Log.d("LocalDocumentRepository", "✅ LocalDocumentRepository ready")
    }

    // ========== DOCUMENT CRUD ==========

    /**
     * Save document locally with pages (Internal Android method)
     * Marks the document as pending sync unless `syncStatus` says otherwise
     */
    internal suspend fun saveDocumentLocallyInternal(
        userId: String,
        title: String,
        type: DocumentType,
        pages: List<SavedPageData>,
        documentId: String? = null, // Optional document ID (for API documents)
        syncStatus: String = "pending", // "synced" for scans that are already on the server
    ): Result<Document> {
        Log.d("LocalDocumentRepository", "═══════════════════════════════════════════════════════")
        Log.d("LocalDocumentRepository", "💾 saveDocumentLocallyInternal called")
        Log.d("LocalDocumentRepository", "   User ID: '$userId'")
        Log.d("LocalDocumentRepository", "   Title: '$title'")
        Log.d("LocalDocumentRepository", "   Type: $type")
        Log.d("LocalDocumentRepository", "   Pages count: ${pages.size}")
        Log.d("LocalDocumentRepository", "   Preset Document ID: ${documentId ?: "none (will generate)"}")

        return try {
            val finalDocumentId = documentId ?: UUID.randomUUID().toString()
            val now = Clock.System.now().toString()
            val syncedAt = if (syncStatus == "synced") now else null

            Log.d("LocalDocumentRepository", "📝 Using document ID: $finalDocumentId")
            Log.d("LocalDocumentRepository", "📝 Timestamp: $now")

            // Save document entity
            val documentEntity =
                DocumentEntity(
                    id = finalDocumentId,
                    userId = userId,
                    title = title,
                    type = type.name,
                    pageCount = pages.size,
                    thumbnailPath = pages.firstOrNull()?.thumbnailPath,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = syncStatus,
                    lastSyncedAt = syncedAt,
                )

            Log.d("LocalDocumentRepository", "💿 Inserting document entity to Room...")
            documentDao.insert(documentEntity)
            Log.d("LocalDocumentRepository", "✅ Document entity inserted")

            // Save page entities
            pages.forEachIndexed { index, page ->
                Log.d("LocalDocumentRepository", "💿 Inserting page ${index + 1}/${pages.size}...")
                val pageEntity =
                    DocumentPageEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = finalDocumentId,
                        pageNumber = page.pageNumber,
                        imagePath = page.imagePath,
                        thumbnailPath = page.thumbnailPath,
                        width = page.width,
                        height = page.height,
                        fileSize = page.fileSize,
                        uploadedAt = now,
                        syncStatus = syncStatus,
                        lastSyncedAt = syncedAt,
                    )
                documentPageDao.insert(pageEntity)
                Log.d("LocalDocumentRepository", "✅ Page ${index + 1} inserted")
            }

            Log.d("LocalDocumentRepository", "✅ Document saved successfully")
            Log.d("LocalDocumentRepository", "   Document ID: $finalDocumentId")
            Log.d("LocalDocumentRepository", "   User ID: '$userId' (THIS IS THE KEY FOR RETRIEVAL!)")
            Log.d("LocalDocumentRepository", "   Title: '$title'")
            Log.d("LocalDocumentRepository", "   Type: $type")
            Log.d("LocalDocumentRepository", "   Pages: ${pages.size}")
            Log.d("LocalDocumentRepository", "   Is Deleted: false (default)")
            Log.d("LocalDocumentRepository", "   Sync Status: $syncStatus")
            Log.d("LocalDocumentRepository", "═══════════════════════════════════════════════════════")
            Result.success(documentEntity.toModel())
        } catch (e: Exception) {
            Log.e("LocalDocumentRepository", "❌ Error saving document", e)
            e.printStackTrace()
            Log.d("LocalDocumentRepository", "═══════════════════════════════════════════════════════")
            Result.failure(e)
        }
    }

    /**
     * Update document metadata (marks as pending sync)
     */
    suspend fun updateDocumentLocally(
        documentId: String,
        title: String? = null,
    ): Result<Unit> {
        return try {
            val now = Clock.System.now().toString()

            title?.let {
                documentDao.updateTitle(documentId, it, now)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get document with pages (offline)
     */
    suspend fun getLocalDocumentWithPages(documentId: String): Result<DocumentWithPages> {
        return try {
            val documentEntity =
                documentDao.getById(documentId)
                    ?: return Result.failure(Exception("Document not found"))

            val pages = documentPageDao.getPagesByDocumentId(documentId)

            Result.success(
                DocumentWithPages(
                    document = documentEntity.toModel(),
                    pages = pages.map { it.toModel() },
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get paging source for documents (offline)
     */
    fun getLocalDocumentsPagingSource(userId: String): PagingSource<Int, DocumentEntity> {
        return documentDao.getDocumentsPagingSource(userId)
    }

    /**
     * Search documents locally
     */
    suspend fun searchLocalDocuments(
        userId: String,
        query: String,
        limit: Int = 50,
    ): Result<List<Document>> {
        Log.d("LocalDocumentRepository", "🔍 searchLocalDocuments called")
        Log.d("LocalDocumentRepository", "   User ID: '$userId'")
        Log.d("LocalDocumentRepository", "   Query: '$query'")
        Log.d("LocalDocumentRepository", "   Limit: $limit")
        return try {
            Log.d("LocalDocumentRepository", "📊 Executing Room query...")
            Log.d(
                "LocalDocumentRepository",
                "   SQL: SELECT * FROM documents WHERE user_id = '$userId' AND is_deleted = 0 AND title LIKE '%$query%'",
            )

            val entities = documentDao.searchDocuments(userId, query, limit)

            Log.d("LocalDocumentRepository", "✅ Query returned ${entities.size} entities")

            if (entities.isEmpty()) {
                Log.w("LocalDocumentRepository", "⚠️ No entities found!")
                Log.w("LocalDocumentRepository", "   Possible reasons:")
                Log.w("LocalDocumentRepository", "   1. userId mismatch: saved with different ID")
                Log.w("LocalDocumentRepository", "   2. is_deleted = 1: documents marked as deleted")
                Log.w("LocalDocumentRepository", "   3. Database empty: no documents saved yet")
            }

            val documents =
                entities.map { entity ->
                    Log.d(
                        "LocalDocumentRepository",
                        "   📄 Entity: ${entity.title} (userId=${entity.userId}, isDeleted=${entity.isDeleted})",
                    )
                    entity.toModel()
                }
            Result.success(documents)
        } catch (e: Exception) {
            Log.e("LocalDocumentRepository", "❌ Search failed", e)
            Result.failure(e)
        }
    }

    /**
     * Delete document (soft delete, marks for sync)
     */
    suspend fun deleteDocumentLocally(documentId: String): Result<Unit> {
        return try {
            val now = Clock.System.now().toString()
            documentDao.markAsDeleted(documentId, now)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hard delete document and all files (local + server)
     *
     * This method performs complete deletion:
     * 1. Delete local files (images, thumbnails)
     * 2. Delete from Room database (pages + document)
     * 3. Delete from server (Firebase Firestore + GCS storage)
     *
     * @param documentId Document ID to delete
     * @param userId User ID (needed for file deletion)
     * @param deleteFromServer If true, also delete from server
     * @return Result indicating success or failure
     */
    actual suspend fun hardDeleteDocument(
        documentId: String,
        userId: String,
        deleteFromServer: Boolean,
    ): Result<Unit> {
        return try {
            Log.d("LocalDocumentRepository", "🗑️ Hard deleting document: $documentId")

            // Step 1: Delete local files (images and thumbnails)
            try {
                fileStorageManager.deleteDocumentFiles(userId, documentId)
                Log.d("LocalDocumentRepository", "✅ Local files deleted")
            } catch (e: Exception) {
                Log.w("LocalDocumentRepository", "⚠️ Failed to delete local files: ${e.message}")
                // Continue with database deletion even if file deletion fails
            }

            // Step 2: Delete from Room database
            // Delete pages first (foreign key constraint)
            documentPageDao.deleteByDocumentId(documentId)
            Log.d("LocalDocumentRepository", "✅ Pages deleted from database")

            // Delete document
            documentDao.hardDelete(documentId)
            Log.d("LocalDocumentRepository", "✅ Document deleted from database")

            // Step 3: Delete from server (optional)
            if (deleteFromServer) {
                try {
                    val apiRepository = com.kashyap.wizlyai.data.repository.DocumentRepository()
                    apiRepository.deleteDocument(documentId).fold(
                        onSuccess = {
                            Log.d("LocalDocumentRepository", "✅ Document deleted from server")
                        },
                        onFailure = { e ->
                            Log.w("LocalDocumentRepository", "⚠️ Failed to delete from server: ${e.message}")
                            // Don't fail the whole operation if server deletion fails
                            // The sync system will handle cleanup later
                        },
                    )
                } catch (e: Exception) {
                    Log.w("LocalDocumentRepository", "⚠️ Server deletion failed: ${e.message}")
                }
            }

            Log.d("LocalDocumentRepository", "✅ Document fully deleted: $documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LocalDocumentRepository", "❌ Error deleting document", e)
            Result.failure(e)
        }
    }

    // ========== PAGE MANAGEMENT ==========

    /**
     * Add page to existing document
     */
    suspend fun addPageLocally(
        documentId: String,
        pageData: SavedPageData,
    ): Result<DocumentPage> {
        return try {
            val now = Clock.System.now().toString()

            val pageEntity =
                DocumentPageEntity(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    pageNumber = pageData.pageNumber,
                    imagePath = pageData.imagePath,
                    thumbnailPath = pageData.thumbnailPath,
                    width = pageData.width,
                    height = pageData.height,
                    fileSize = pageData.fileSize,
                    uploadedAt = now,
                    syncStatus = "pending",
                )
            documentPageDao.insert(pageEntity)

            // Update document page count
            val currentCount = documentPageDao.getPageCount(documentId)
            documentDao.updatePageCount(documentId, currentCount, now)

            // Update thumbnail if it's the first page
            if (pageData.pageNumber == 1 && pageData.thumbnailPath != null) {
                documentDao.updateThumbnailPath(documentId, pageData.thumbnailPath!!)
            }

            Result.success(pageEntity.toModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete page
     */
    actual suspend fun deletePageLocally(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
    ): Result<Unit> {
        return try {
            val now = Clock.System.now().toString()

            // Delete files
            fileStorageManager.deletePageFiles(userId, documentId, pageNumber)

            // Delete database entry
            documentPageDao.markAsDeleted(pageId, now)

            // Update document page count
            val currentCount = documentPageDao.getPageCount(documentId)
            documentDao.updatePageCount(documentId, currentCount, now)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== FILE OPERATIONS ==========

    /**
     * Save image from temp location with compression
     *
     * @param scanQuality User's scan quality setting (defaults to LARGE)
     */
    suspend fun saveImageToInternalStorage(
        tempFilePath: String,
        userId: String,
        documentId: String,
        pageNumber: Int,
        scanQuality: com.kashyap.wizlyai.data.models.ScanQuality = com.kashyap.wizlyai.data.models.ScanQuality.LARGE,
    ): Result<SavedImagePaths> {
        return fileStorageManager.saveImage(tempFilePath, userId, documentId, pageNumber, scanQuality)
    }

    /**
     * Prepare image for upload
     */
    suspend fun prepareImageForUpload(imagePath: String): Result<java.io.File> {
        return fileStorageManager.prepareForUpload(imagePath)
    }

    // ========== SYNC QUEUE ==========

    /**
     * Get pending documents for sync
     */
    suspend fun getPendingSyncDocuments(): Result<List<DocumentEntity>> {
        return try {
            val entities = documentDao.getPendingSyncDocuments()
            Result.success(entities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get pending pages for a document
     */
    suspend fun getPendingSyncPages(documentId: String): Result<List<DocumentPageEntity>> {
        return try {
            val pages = documentPageDao.getPendingSyncPagesByDocument(documentId)
            Result.success(pages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get pending sync count (observable)
     */
    actual fun getPendingSyncCountFlow(): Flow<Int> {
        return documentDao.getPendingSyncCount()
    }

    /**
     * Mark document as synced
     */
    suspend fun markDocumentSynced(documentId: String): Result<Unit> {
        return try {
            val now = Clock.System.now().toString()
            documentDao.updateSyncStatus(documentId, "synced", now)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark document sync as failed
     */
    suspend fun markDocumentSyncFailed(documentId: String): Result<Unit> {
        return try {
            documentDao.updateSyncStatus(documentId, "failed", null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark page as synced
     */
    suspend fun markPageSynced(pageId: String): Result<Unit> {
        return try {
            val now = Clock.System.now().toString()
            documentPageDao.updateSyncStatus(pageId, "synced", now)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== STATISTICS ==========

    /**
     * Get total document count for the user
     */
    suspend fun getDocumentCount(userId: String): Int {
        return documentDao.getDocumentCount(userId)
    }

    /**
     * Get storage usage
     */
    actual suspend fun getTotalStorageUsed(): Long {
        return fileStorageManager.getTotalStorageUsed()
    }

    /**
     * Cleanup synced deleted documents
     */
    suspend fun cleanupSyncedDeleted(): Result<Unit> {
        return try {
            documentDao.cleanupSyncedDeleted()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== COMMON INTERFACE IMPLEMENTATIONS ==========

    /**
     * Save document locally with image paths (common interface)
     * Wrapper for saveDocumentLocally(pages)
     */
    actual suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: com.kashyap.wizlyai.data.models.DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        // Convert image paths to SavedPageData
        val pages =
            imagePaths.mapIndexed { index, path ->
                SavedPageData(
                    pageNumber = index + 1,
                    imagePath = path,
                    thumbnailPath = null,
                    width = null,
                    height = null,
                    fileSize = 0L,
                )
            }

        return saveDocumentLocallyInternal(
            userId = userId,
            title = title,
            type = type,
            pages = pages,
        ).map { it.id }
    }

    /**
     * Save document with specific ID (for API documents)
     * Preserves the document ID from the server
     */
    suspend fun saveDocumentWithId(
        documentId: String,
        userId: String,
        title: String,
        type: com.kashyap.wizlyai.data.models.DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        Log.d("LocalDocumentRepository", "💾 saveDocumentWithId called (preserving API document ID)")
        Log.d("LocalDocumentRepository", "   Preserving document ID: $documentId")

        // Convert image paths to SavedPageData
        val pages =
            imagePaths.mapIndexed { index, path ->
                SavedPageData(
                    pageNumber = index + 1,
                    imagePath = path,
                    thumbnailPath = null,
                    width = null,
                    height = null,
                    fileSize = 0L,
                )
            }

        return saveDocumentLocallyInternal(
            documentId = documentId, // Pass the document ID to preserve it
            userId = userId,
            title = title,
            type = type,
            pages = pages,
        ).map { it.id }
    }

    /**
     * Get document by ID (common interface)
     */
    actual suspend fun getLocalDocument(documentId: String): Result<com.kashyap.wizlyai.data.models.Document> {
        return getLocalDocumentWithPages(documentId).map { it.document }
    }

    /**
     * Add page with image path (common interface)
     */
    actual suspend fun addPageLocally(
        documentId: String,
        imagePath: String,
    ): Result<Unit> {
        val pageData =
            SavedPageData(
                pageNumber = 0, // Will be calculated
                imagePath = imagePath,
                thumbnailPath = null,
                width = null,
                height = null,
                fileSize = 0L,
            )
        return addPageLocally(documentId, pageData).map { }
    }

    // Note: deletePageLocally already matches interface signature
    // Note: hardDeleteDocument already matches interface signature

    /**
     * Mark for sync (common interface)
     */
    actual suspend fun markForSync(documentId: String): Result<Unit> {
        val now = Clock.System.now().toString()
        return try {
            documentDao.updateSyncStatus(documentId, "pending", now)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Note: getTotalStorageUsed already matches interface signature
}

/**
 * Saved page data after compression
 */
data class SavedPageData(
    val pageNumber: Int,
    val imagePath: String,
    val thumbnailPath: String?,
    val width: Int?,
    val height: Int?,
    val fileSize: Long,
)
