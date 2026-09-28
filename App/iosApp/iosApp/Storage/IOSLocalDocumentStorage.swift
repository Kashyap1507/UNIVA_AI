import Foundation
import shared

/**
 * iOS implementation of LocalDocumentStorage
 * Uses CoreData for persistence and FileStorageManager for files.
 * Scans are owned by the signed-in student (userId).
 */
@MainActor
class IOSLocalDocumentStorage {

    // MARK: - Properties

    private let database: WizlyDatabase
    private let fileStorage: FileStorageManager

    // MARK: - Initialization

    init() {
        self.database = WizlyDatabase.shared
        self.fileStorage = FileStorageManager.shared
    }

    // MARK: - LocalDocumentStorage Methods

    func saveDocumentLocally(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: [String]
    ) async throws -> String {
        let documentId = UUID().uuidString
        let now = ISO8601DateFormatter().string(from: Date())

        _ = try database.documentRepository.insert(
            DocumentData(
                id: documentId,
                userId: userId,
                title: title,
                type: type.name,
                pageCount: imagePaths.count,
                thumbnailPath: nil,
                status: "PENDING",
                processingProgress: 0,
                createdAt: now,
                updatedAt: now,
                syncStatus: "pending",
                lastSyncedAt: nil,
                isDeleted: false,
                deletedAt: nil
            )
        )

        for (index, imagePath) in imagePaths.enumerated() {
            _ = try database.documentPageRepository.insert(
                newPage(documentId: documentId, pageNumber: index + 1, imagePath: imagePath, uploadedAt: now)
            )
        }

        print("✅ IOSLocalDocumentStorage: Saved document \(documentId) with \(imagePaths.count) pages")
        return documentId
    }

    func addPageLocally(documentId: String, imagePath: String) async throws {
        guard let document = database.documentRepository.getById(documentId) else {
            throw notFound("Document not found")
        }

        let now = ISO8601DateFormatter().string(from: Date())
        _ = try database.documentPageRepository.insert(
            newPage(documentId: documentId, pageNumber: document.pageCount + 1, imagePath: imagePath, uploadedAt: now)
        )
        try database.documentRepository.update(
            copy(document, pageCount: document.pageCount + 1, updatedAt: now, syncStatus: "pending")
        )
        print("✅ IOSLocalDocumentStorage: Added page to document \(documentId)")
    }

    func deletePageLocally(documentId: String, pageId: String, userId: String, pageNumber: Int) async throws {
        guard let page = database.documentPageRepository.getById(pageId) else {
            throw notFound("Page not found")
        }

        _ = fileStorage.deleteImage(atPath: page.imagePath)
        if let thumbnailPath = page.thumbnailPath {
            _ = fileStorage.deleteImage(atPath: thumbnailPath)
        }
        try database.documentPageRepository.delete(pageId)

        guard let document = database.documentRepository.getById(documentId) else { return }
        let now = ISO8601DateFormatter().string(from: Date())
        try database.documentRepository.update(
            copy(document, pageCount: max(0, document.pageCount - 1), updatedAt: now, syncStatus: "pending")
        )
        print("✅ IOSLocalDocumentStorage: Deleted page \(pageNumber) from document \(documentId)")
    }

    func deleteDocumentLocally(documentId: String) async throws {
        try await IOSDocumentProvider().deleteDocument(documentId: documentId)
    }

    func getLocalDocument(documentId: String) async throws -> Document {
        guard let documentData = database.documentRepository.getById(documentId) else {
            throw notFound("Document not found")
        }
        return toDocument(documentData, pages: nil)
    }

    func markForSync(documentId: String) async throws {
        guard let document = database.documentRepository.getById(documentId) else {
            throw notFound("Document not found")
        }
        let now = ISO8601DateFormatter().string(from: Date())
        try database.documentRepository.update(copy(document, pageCount: document.pageCount, updatedAt: now, syncStatus: "pending"))
        print("✅ IOSLocalDocumentStorage: Marked document \(documentId) for sync")
    }

    func getStorageUsed() async -> Int64 {
        return fileStorage.getStorageUsed()
    }

    // MARK: - Document Query Methods

    /**
     * Get the student's documents, newest first
     */
    func getDocuments(userId: String) async throws -> [Document] {
        let documents = database.documentRepository.getByUserId(userId).map { toDocument($0, pages: nil) }
        print("✅ IOSLocalDocumentStorage: Returning \(documents.count) documents")
        return documents
    }

    /**
     * Search documents by title
     */
    func searchDocuments(userId: String, query: String, limit: Int) async throws -> [Document] {
        let matches = try await getDocuments(userId: userId).filter { $0.title.localizedCaseInsensitiveContains(query) }
        return Array(matches.prefix(limit))
    }

    /**
     * Number of (not deleted) documents for the student
     */
    func getDocumentCount(userId: String) async -> Int {
        return database.documentRepository.getByUserId(userId).filter { !$0.isDeleted }.count
    }

    /**
     * Get document with pages
     */
    func getDocumentWithPages(documentId: String) async throws -> DocumentWithPages {
        guard let documentData = database.documentRepository.getById(documentId) else {
            throw notFound("Document not found")
        }

        let pages: [DocumentPage] = database.documentPageRepository.getByDocumentId(documentId)
            .filter { !$0.isDeleted }
            .map { pageData in
                DocumentPage(
                    id: pageData.id,
                    documentId: pageData.documentId,
                    pageNumber: Int32(pageData.pageNumber),
                    imageUrl: pageData.imagePath,
                    thumbnailUrl: pageData.thumbnailPath,
                    width: pageData.width.map { KotlinInt(value: Int32($0)) },
                    height: pageData.height.map { KotlinInt(value: Int32($0)) },
                    fileSize: pageData.fileSize.map { KotlinLong(value: Int64($0)) },
                    status: ProcessingStatus.pending,
                    uploadedAt: pageData.uploadedAt,
                    processedAt: nil
                )
            }

        print("✅ IOSLocalDocumentStorage: Loaded document \(documentId) with \(pages.count) pages")
        return DocumentWithPages(document: toDocument(documentData, pages: nil), pages: pages)
    }

    // MARK: - Helpers

    private func toDocument(_ documentData: DocumentData, pages: [DocumentPage]?) -> Document {
        let docType: DocumentType = documentData.type.uppercased() == "WORKSHEET" ? .worksheet : .homework
        return Document(
            id: documentData.id,
            userId: documentData.userId,
            title: documentData.title,
            type: docType,
            pageCount: Int32(documentData.pageCount),
            pages: pages,
            thumbnailUrl: documentData.thumbnailPath,
            status: ProcessingStatus.pending,
            processingProgress: Int32(documentData.processingProgress),
            createdAt: documentData.createdAt,
            updatedAt: documentData.updatedAt
        )
    }

    private func newPage(documentId: String, pageNumber: Int, imagePath: String, uploadedAt: String) -> DocumentPageData {
        return DocumentPageData(
            id: UUID().uuidString,
            documentId: documentId,
            pageNumber: pageNumber,
            imagePath: imagePath,
            thumbnailPath: nil,
            width: nil,
            height: nil,
            fileSize: nil,
            uploadedAt: uploadedAt,
            syncStatus: "pending",
            lastSyncedAt: nil,
            isDeleted: false,
            deletedAt: nil
        )
    }

    private func copy(_ document: DocumentData, pageCount: Int, updatedAt: String, syncStatus: String) -> DocumentData {
        return DocumentData(
            id: document.id,
            userId: document.userId,
            title: document.title,
            type: document.type,
            pageCount: pageCount,
            thumbnailPath: document.thumbnailPath,
            status: document.status,
            processingProgress: document.processingProgress,
            createdAt: document.createdAt,
            updatedAt: updatedAt,
            syncStatus: syncStatus,
            lastSyncedAt: document.lastSyncedAt,
            isDeleted: document.isDeleted,
            deletedAt: document.deletedAt
        )
    }

    private func notFound(_ message: String) -> NSError {
        return NSError(domain: "IOSLocalDocumentStorage", code: 404, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
