package com.kashyap.wizlyai.data.repository

import android.content.Context
import android.util.Log
import com.kashyap.wizlyai.data.models.Document
import com.kashyap.wizlyai.data.models.DocumentType
import com.kashyap.wizlyai.data.models.ScanQuality

/**
 * Android implementation of LocalDocumentStorage using Room database
 */
class AndroidLocalDocumentStorage(private val context: Context) : LocalDocumentStorage {
    private val localRepository: LocalDocumentRepository
    private val appSettingsRepository: com.kashyap.wizlyai.data.repository.AppSettingsRepository

    init {
        Log.d("AndroidLocalDocumentStorage", "📦 Initializing AndroidLocalDocumentStorage")
        localRepository = LocalDocumentRepository(context)
        appSettingsRepository = createAppSettingsRepository(context)
        Log.d("AndroidLocalDocumentStorage", "✅ AndroidLocalDocumentStorage initialized")
    }

    /**
     * Get current scan quality from user settings
     * Defaults to LARGE if no user is logged in or setting not found
     */
    private suspend fun getCurrentScanQuality(): ScanQuality {
        val userId = com.kashyap.wizlyai.session.UserSessionManager.instance.userId
        if (userId == null) {
            Log.d("AndroidLocalDocumentStorage", "⚠️ No user logged in, using default LARGE quality")
            return ScanQuality.LARGE
        }

        return appSettingsRepository.getSettings(userId).fold(
            onSuccess = { settings ->
                try {
                    val quality = ScanQuality.valueOf(settings.scanQuality)
                    Log.d("AndroidLocalDocumentStorage", "📷 Using scan quality: ${quality.name}")
                    quality
                } catch (e: Exception) {
                    Log.w("AndroidLocalDocumentStorage", "⚠️ Invalid scan quality '${settings.scanQuality}', using LARGE")
                    ScanQuality.LARGE
                }
            },
            onFailure = { error ->
                Log.w("AndroidLocalDocumentStorage", "⚠️ Failed to get scan quality: ${error.message}, using LARGE")
                ScanQuality.LARGE
            },
        )
    }

    override suspend fun saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        Log.d("AndroidLocalDocumentStorage", "📥 saveDocumentLocally called")
        Log.d("AndroidLocalDocumentStorage", "   userId: $userId")
        Log.d("AndroidLocalDocumentStorage", "   title: $title")
        Log.d("AndroidLocalDocumentStorage", "   type: $type")
        Log.d("AndroidLocalDocumentStorage", "   imagePaths count: ${imagePaths.size}")

        // Get current scan quality setting
        val scanQuality = getCurrentScanQuality()
        Log.d("AndroidLocalDocumentStorage", "📷 Applying scan quality: ${scanQuality.name} (${scanQuality.getDimensionString()})")

        val savedPages =
            savePagesToInternalStorage(userId, imagePaths, scanQuality) { "temp_${System.currentTimeMillis()}" }
                .getOrElse { return Result.failure(it) }

        Log.d("AndroidLocalDocumentStorage", "💾 Saving document to Room database...")
        // Save document with pages to Room database
        return localRepository.saveDocumentLocallyInternal(
            userId = userId,
            title = title,
            type = type,
            pages = savedPages,
        ).fold(
            onSuccess = { document ->
                Log.d("AndroidLocalDocumentStorage", "✅ Document saved to database: ${document.id}")
                Log.d("AndroidLocalDocumentStorage", "🔄 Triggering immediate sync to backend...")

                // Trigger immediate sync to upload document to backend
                com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.syncDocuments()

                Result.success(document.id)
            },
            onFailure = { error ->
                Log.e("AndroidLocalDocumentStorage", "❌ Failed to save to database", error)
                Result.failure(error)
            },
        )
    }

    /**
     * Compress each captured image into internal storage.
     * `folderFor` names the per-document folder (called once per page).
     */
    private suspend fun savePagesToInternalStorage(
        userId: String,
        imagePaths: List<String>,
        scanQuality: ScanQuality,
        folderFor: () -> String,
    ): Result<List<SavedPageData>> {
        val savedPages = mutableListOf<SavedPageData>()
        imagePaths.forEachIndexed { index, imagePath ->
            val pageNumber = index + 1
            Log.d("AndroidLocalDocumentStorage", "📄 Processing page $pageNumber: $imagePath")
            localRepository.saveImageToInternalStorage(
                tempFilePath = imagePath,
                userId = userId,
                documentId = folderFor(),
                pageNumber = pageNumber,
                scanQuality = scanQuality,
            ).fold(
                onSuccess = { savedPaths ->
                    Log.d("AndroidLocalDocumentStorage", "✅ Image compressed and saved")
                    savedPages.add(
                        SavedPageData(
                            pageNumber = pageNumber,
                            imagePath = savedPaths.originalPath,
                            thumbnailPath = savedPaths.thumbnailPath,
                            width = savedPaths.width,
                            height = savedPaths.height,
                            fileSize = savedPaths.fileSize,
                        ),
                    )
                },
                onFailure = { error ->
                    Log.e("AndroidLocalDocumentStorage", "❌ Failed to save image", error)
                    return Result.failure(error)
                },
            )
        }
        return Result.success(savedPages)
    }

    override suspend fun saveUploadedScan(
        documentId: String,
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        Log.d("AndroidLocalDocumentStorage", "📥 saveUploadedScan: $documentId (${imagePaths.size} pages, already on the server)")
        val pages =
            savePagesToInternalStorage(userId, imagePaths, getCurrentScanQuality()) { documentId }
                .getOrElse { return Result.failure(it) }
        return localRepository.saveDocumentLocallyInternal(
            userId = userId,
            title = title,
            type = type,
            pages = pages,
            documentId = documentId,
            syncStatus = "synced",
        ).map { it.id }
    }

    override suspend fun deleteDocumentLocally(
        documentId: String,
        userId: String,
    ): Result<Unit> = localRepository.hardDeleteDocument(documentId = documentId, userId = userId, deleteFromServer = false)

    override suspend fun saveDocumentWithId(
        documentId: String,
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: List<String>,
    ): Result<String> {
        Log.d("AndroidLocalDocumentStorage", "📥 saveDocumentWithId called (preserving API document ID)")
        Log.d("AndroidLocalDocumentStorage", "   documentId: $documentId (from API)")
        Log.d("AndroidLocalDocumentStorage", "   userId: $userId")
        Log.d("AndroidLocalDocumentStorage", "   title: $title")
        Log.d("AndroidLocalDocumentStorage", "   type: $type")
        Log.d("AndroidLocalDocumentStorage", "   imagePaths count: ${imagePaths.size}")

        // For API documents, image paths are URLs, not local paths
        // We don't need to compress/save them to internal storage since they're already hosted
        // Just create SavedPageData entries with the URLs
        val savedPages =
            imagePaths.mapIndexed { index, imageUrl ->
                SavedPageData(
                    pageNumber = index + 1,
                    imagePath = imageUrl, // Use URL directly
                    thumbnailPath = imageUrl, // Use same URL for thumbnail
                    width = null, // Unknown dimensions from URL
                    height = null,
                    fileSize = 0L, // Unknown file size
                )
            }

        Log.d("AndroidLocalDocumentStorage", "💾 Saving document to Room database with preserved ID...")
        // Save document with pages to Room database, preserving the API document ID
        return localRepository.saveDocumentLocallyInternal(
            documentId = documentId, // PRESERVE the API document ID
            userId = userId,
            title = title,
            type = type,
            pages = savedPages,
        ).fold(
            onSuccess = { document ->
                Log.d("AndroidLocalDocumentStorage", "✅ Document saved to database with preserved ID: ${document.id}")
                if (document.id != documentId) {
                    Log.e("AndroidLocalDocumentStorage", "⚠️ WARNING: Saved ID (${document.id}) doesn't match API ID ($documentId)!")
                }
                Result.success(document.id)
            },
            onFailure = { error ->
                Log.e("AndroidLocalDocumentStorage", "❌ Failed to save to database", error)
                Result.failure(error)
            },
        )
    }

    override suspend fun addPageLocally(
        documentId: String,
        imagePath: String,
    ): Result<Unit> {
        // Get current page count to determine page number
        val documentResult = localRepository.getLocalDocumentWithPages(documentId)
        if (documentResult.isFailure) {
            return Result.failure(documentResult.exceptionOrNull()!!)
        }

        val document = documentResult.getOrNull()!!
        val pageNumber = document.pages.size + 1

        // Get current scan quality setting
        val scanQuality = getCurrentScanQuality()

        // Save image to internal storage with quality preset
        return localRepository.saveImageToInternalStorage(
            tempFilePath = imagePath,
            userId = document.document.userId,
            documentId = documentId,
            pageNumber = pageNumber,
            scanQuality = scanQuality,
        ).fold(
            onSuccess = { savedPaths ->
                val pageData =
                    SavedPageData(
                        pageNumber = pageNumber,
                        imagePath = savedPaths.originalPath,
                        thumbnailPath = savedPaths.thumbnailPath,
                        width = savedPaths.width,
                        height = savedPaths.height,
                        fileSize = savedPaths.fileSize,
                    )

                localRepository.addPageLocally(documentId, pageData).fold(
                    onSuccess = {
                        // Trigger sync to upload new page
                        com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.syncDocuments()
                        Result.success(Unit)
                    },
                    onFailure = { error -> Result.failure(error) },
                )
            },
            onFailure = { error -> Result.failure(error) },
        )
    }

    override suspend fun deletePageLocally(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
    ): Result<Unit> {
        return localRepository.deletePageLocally(documentId, pageId, userId, pageNumber)
    }

    override suspend fun getLocalDocument(documentId: String): Result<Document> {
        return localRepository.getLocalDocumentWithPages(documentId).fold(
            onSuccess = { documentWithPages -> Result.success(documentWithPages.document) },
            onFailure = { error -> Result.failure(error) },
        )
    }

    override suspend fun markForSync(documentId: String): Result<Unit> {
        // Documents are automatically marked as pending sync when created/modified
        // This is a no-op but can be used to explicitly trigger sync
        return Result.success(Unit)
    }

    override suspend fun getStorageUsed(): Long {
        return localRepository.getTotalStorageUsed()
    }
}
