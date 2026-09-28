import Foundation
import shared

/**
 * iOS implementation of DocumentProvider
 * Provides documents from CoreData with search and filtering
 */
@MainActor
class IOSDocumentProvider {

    // MARK: - Properties

    private let database: WizlyDatabase
    private let fileStorage: FileStorageManager

    // MARK: - Initialization

    init() {
        self.database = WizlyDatabase.shared
        self.fileStorage = FileStorageManager.shared
    }

    // MARK: - DocumentProvider Methods

    func getDocuments(userId: String) -> [Document] {
        let documents = database.documentRepository.getByUserId(userId)

        return documents.map { convertToDocument($0) }
    }

    func searchDocuments(userId: String, query: String) async -> [Document] {
        let allDocuments = database.documentRepository.getByUserId(userId)

        // Filter by query
        let filteredDocuments = allDocuments.filter { document in
            document.title.localizedCaseInsensitiveContains(query)
        }

        return filteredDocuments.map { convertToDocument($0) }
    }

    func deleteDocument(documentId: String) async throws {
        // Get document
        guard let document = database.documentRepository.getById(documentId) else {
            throw NSError(domain: "IOSDocumentProvider", code: 404, userInfo: [NSLocalizedDescriptionKey: "Document not found"])
        }

        // Get pages
        let pages = database.documentPageRepository.getByDocumentId(documentId)

        // Delete image files
        for page in pages {
            _ = fileStorage.deleteImage(atPath: page.imagePath)
            if let thumbnailPath = page.thumbnailPath {
                _ = fileStorage.deleteImage(atPath: thumbnailPath)
            }

            // Delete page from database
            try database.documentPageRepository.delete(page.id)
        }

        // Delete document thumbnail
        if let thumbnailPath = document.thumbnailPath {
            _ = fileStorage.deleteImage(atPath: thumbnailPath)
        }

        // Delete document from database
        try database.documentRepository.delete(documentId)
        print("✅ IOSDocumentProvider: Deleted document \(documentId)")
    }

    func getPendingSyncCount() async -> Int {
        let pendingDocuments = database.documentRepository.getPendingSync()
        return pendingDocuments.count
    }

    func getStorageUsed() async -> Int64 {
        return fileStorage.getStorageUsed()
    }

    // MARK: - Helper Methods

    private func convertToDocument(_ documentData: DocumentData) -> Document {
        return Document(
            id: documentData.id,
            userId: documentData.userId,
            title: documentData.title,
            type: DocumentType.homework,
            pageCount: Int32(documentData.pageCount),
            pages: nil,
            thumbnailUrl: documentData.thumbnailPath,
            status: ProcessingStatus.pending,
            processingProgress: Int32(documentData.processingProgress),
            createdAt: documentData.createdAt,
            updatedAt: documentData.updatedAt
        )
    }
}
