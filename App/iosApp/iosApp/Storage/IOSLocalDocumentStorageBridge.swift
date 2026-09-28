import Foundation
import shared

/**
 * Bridge to expose IOSLocalDocumentStorage to Kotlin code
 * This is called from IOSLocalDocumentStorageWrapper.kt
 */
@MainActor
@objc public class IOSLocalDocumentStorageBridge: NSObject {

    @objc public static let shared = IOSLocalDocumentStorageBridge()

    private let storage: IOSLocalDocumentStorage

    private override init() {
        self.storage = IOSLocalDocumentStorage()
        super.init()
    }

    // MARK: - Document Operations

    /**
     * Save a new document with pages
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func saveDocument(
        userId: String,
        title: String,
        type: DocumentType,
        imagePaths: [String],
        completion: @escaping (String?, Error?) -> Void
    ) {
        Task {
            do {
                let documentId = try await storage.saveDocumentLocally(
                    userId: userId,
                    title: title,
                    type: type,
                    imagePaths: imagePaths
                )
                completion(documentId, nil)
            } catch {
                completion(nil, error)
            }
        }
    }

    /**
     * Add a page to an existing document
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func addPage(
        documentId: String,
        imagePath: String,
        completion: @escaping (Error?) -> Void
    ) {
        Task {
            do {
                try await storage.addPageLocally(documentId: documentId, imagePath: imagePath)
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    /**
     * Delete a page from a document
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func deletePage(
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
        completion: @escaping (Error?) -> Void
    ) {
        Task {
            do {
                try await storage.deletePageLocally(
                    documentId: documentId,
                    pageId: pageId,
                    userId: userId,
                    pageNumber: pageNumber
                )
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    /**
     * Delete a scan and its pages from this device
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func deleteDocument(
        documentId: String,
        completion: @escaping (Error?) -> Void
    ) {
        Task {
            do {
                try await storage.deleteDocumentLocally(documentId: documentId)
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    /**
     * Get a local document by ID
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func getDocument(
        documentId: String,
        completion: @escaping (Document?, Error?) -> Void
    ) {
        Task {
            do {
                let document = try await storage.getLocalDocument(documentId: documentId)
                completion(document, nil)
            } catch {
                completion(nil, error)
            }
        }
    }

    /**
     * Mark a document for sync
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func markForSync(
        documentId: String,
        completion: @escaping (Error?) -> Void
    ) {
        Task {
            do {
                try await storage.markForSync(documentId: documentId)
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }

    /**
     * Get total storage used in bytes
     * Called from Kotlin IOSLocalDocumentStorageWrapper
     */
    @objc public func getStorageUsed(completion: @escaping (Int64) -> Void) {
        Task {
            let storageUsed = await storage.getStorageUsed()
            completion(storageUsed)
        }
    }

    // MARK: - Document Query Operations

    /**
     * Get all documents for the student
     * Called from Kotlin IOSLocalDocumentRepository
     */
    @objc public func getDocuments(
        userId: String,
        completion: @escaping ([Document]?, Error?) -> Void
    ) {
        Task {
            do {
                let documents = try await storage.getDocuments(userId: userId)
                completion(documents, nil)
            } catch {
                completion(nil, error)
            }
        }
    }

    /**
     * Search documents by title
     * Called from Kotlin IOSLocalDocumentRepository
     */
    @objc public func searchDocuments(
        userId: String,
        query: String,
        limit: Int,
        completion: @escaping ([Document]?, Error?) -> Void
    ) {
        Task {
            do {
                let documents = try await storage.searchDocuments(userId: userId, query: query, limit: limit)
                completion(documents, nil)
            } catch {
                completion(nil, error)
            }
        }
    }

    /**
     * Get document count for the student
     * Called from Kotlin IOSLocalDocumentRepository
     */
    @objc public func getDocumentCount(
        userId: String,
        completion: @escaping (Int) -> Void
    ) {
        Task {
            let count = await storage.getDocumentCount(userId: userId)
            completion(count)
        }
    }

    /**
     * Get document with pages
     * Called from Kotlin IOSLocalDocumentRepository
     */
    @objc public func getDocumentWithPages(
        documentId: String,
        completion: @escaping (DocumentWithPages?, Error?) -> Void
    ) {
        Task {
            do {
                let docWithPages = try await storage.getDocumentWithPages(documentId: documentId)
                completion(docWithPages, nil)
            } catch {
                completion(nil, error)
            }
        }
    }
}
