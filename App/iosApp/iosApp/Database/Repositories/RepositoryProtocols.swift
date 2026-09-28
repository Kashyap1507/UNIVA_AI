import Foundation
@preconcurrency import CoreData
import shared

// MARK: - Repository Protocols

@MainActor
protocol UserRepository {
    func insert(_ user: UserData) throws -> Int64
    func getById(_ id: String) -> UserData?
    func getByEmail(_ email: String) -> UserData?
    func update(_ user: UserData) throws
    func delete(_ id: String) throws
    func deleteAll() throws
}

@MainActor
protocol DocumentRepository {
    func insert(_ document: DocumentData) throws -> Int64
    func getById(_ id: String) -> DocumentData?
    func getByUserId(_ userId: String) -> [DocumentData]
    func getPendingSync() -> [DocumentData]
    func adoptDocuments(userId: String) throws -> Int
    func update(_ document: DocumentData) throws
    func delete(_ id: String) throws
    func deleteAll() throws
}

@MainActor
protocol DocumentPageRepository {
    func insert(_ page: DocumentPageData) throws -> Int64
    func getById(_ id: String) -> DocumentPageData?
    func getByDocumentId(_ documentId: String) -> [DocumentPageData]
    func update(_ page: DocumentPageData) throws
    func delete(_ id: String) throws
    func deleteAll() throws
}

// MARK: - Data Models (Swift structs matching Kotlin entities)

struct UserData {
    let id: String
    let email: String
    let name: String?
    let authProvider: String
    let createdAt: String
    let updatedAt: String
    let lastLogin: String?
    let syncStatus: String
}

struct DocumentData {
    let id: String
    let userId: String
    let title: String
    let type: String
    let pageCount: Int
    let thumbnailPath: String?
    let status: String
    let processingProgress: Int
    let createdAt: String
    let updatedAt: String
    let syncStatus: String
    let lastSyncedAt: String?
    let isDeleted: Bool
    let deletedAt: String?
}

struct DocumentPageData {
    let id: String
    let documentId: String
    let pageNumber: Int
    let imagePath: String
    let thumbnailPath: String?
    let width: Int?
    let height: Int?
    let fileSize: Int64?
    let uploadedAt: String
    let syncStatus: String
    let lastSyncedAt: String?
    let isDeleted: Bool
    let deletedAt: String?
}
