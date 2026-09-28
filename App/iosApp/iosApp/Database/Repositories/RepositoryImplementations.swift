import Foundation
@preconcurrency import CoreData

// MARK: - User Repository Implementation
class UserRepositoryImpl: UserRepository {
    private let database: WizlyDatabase

    init(database: WizlyDatabase) {
        self.database = database
    }

    func insert(_ user: UserData) throws -> Int64 {
        let context = database.context
        let entity = NSEntityDescription.insertNewObject(forEntityName: "UserEntity", into: context)

        entity.setValue(user.id, forKey: "id")
        entity.setValue(user.email, forKey: "email")
        entity.setValue(user.name, forKey: "name")
        entity.setValue(user.authProvider, forKey: "authProvider")
        entity.setValue(user.createdAt, forKey: "createdAt")
        entity.setValue(user.updatedAt, forKey: "updatedAt")
        entity.setValue(user.lastLogin, forKey: "lastLogin")
        entity.setValue(user.syncStatus, forKey: "syncStatus")

        try database.saveContext()
        return 1
    }

    func getById(_ id: String) -> UserData? {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "UserEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", id)

        do {
            let results = try database.context.fetch(fetchRequest)
            return results.first.flatMap { convertToUserData($0) }
        } catch {
            print("Error fetching user: \(error)")
            return nil
        }
    }

    func getByEmail(_ email: String) -> UserData? {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "UserEntity")
        fetchRequest.predicate = NSPredicate(format: "email == %@", email)

        do {
            let results = try database.context.fetch(fetchRequest)
            return results.first.flatMap { convertToUserData($0) }
        } catch {
            print("Error fetching user by email: \(error)")
            return nil
        }
    }

    func update(_ user: UserData) throws {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "UserEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", user.id)

        let results = try database.context.fetch(fetchRequest)
        if let entity = results.first {
            entity.setValue(user.email, forKey: "email")
            entity.setValue(user.name, forKey: "name")
            entity.setValue(user.authProvider, forKey: "authProvider")
            entity.setValue(user.updatedAt, forKey: "updatedAt")
            entity.setValue(user.lastLogin, forKey: "lastLogin")
            entity.setValue(user.syncStatus, forKey: "syncStatus")

            try database.saveContext()
        }
    }

    func delete(_ id: String) throws {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "UserEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", id)

        let results = try database.context.fetch(fetchRequest)
        for object in results {
            database.context.delete(object)
        }

        try database.saveContext()
    }

    func deleteAll() throws {
        let fetchRequest = NSFetchRequest<NSFetchRequestResult>(entityName: "UserEntity")
        let deleteRequest = NSBatchDeleteRequest(fetchRequest: fetchRequest)

        try database.context.execute(deleteRequest)
        try database.saveContext()
    }

    private func convertToUserData(_ object: NSManagedObject) -> UserData? {
        guard let id = object.value(forKey: "id") as? String,
              let email = object.value(forKey: "email") as? String,
              let authProvider = object.value(forKey: "authProvider") as? String,
              let createdAt = object.value(forKey: "createdAt") as? String,
              let updatedAt = object.value(forKey: "updatedAt") as? String,
              let syncStatus = object.value(forKey: "syncStatus") as? String else {
            return nil
        }

        return UserData(
            id: id,
            email: email,
            name: object.value(forKey: "name") as? String,
            authProvider: authProvider,
            createdAt: createdAt,
            updatedAt: updatedAt,
            lastLogin: object.value(forKey: "lastLogin") as? String,
            syncStatus: syncStatus
        )
    }
}

// MARK: - Document Repository Implementation
class DocumentRepositoryImpl: DocumentRepository {
    private let database: WizlyDatabase

    init(database: WizlyDatabase) {
        self.database = database
    }

    func insert(_ document: DocumentData) throws -> Int64 {
        let context = database.context

        // Upsert: update the existing row if the id is already stored
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", document.id)

        let entity: NSManagedObject
        if let existing = try context.fetch(fetchRequest).first {
            entity = existing
        } else {
            entity = NSEntityDescription.insertNewObject(forEntityName: "DocumentEntity", into: context)
            entity.setValue(document.id, forKey: "id")
        }

        entity.setValue(document.userId, forKey: "userId")
        apply(document, to: entity)
        try database.saveContext()
        return 1
    }

    func getById(_ id: String) -> DocumentData? {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", id)

        do {
            return try database.context.fetch(fetchRequest).first.flatMap { convertToDocumentData($0) }
        } catch {
            print("Error fetching document: \(error)")
            return nil
        }
    }

    func getByUserId(_ userId: String) -> [DocumentData] {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "userId == %@ AND deletedFlag == NO", userId)
        fetchRequest.sortDescriptors = [NSSortDescriptor(key: "createdAt", ascending: false)]

        do {
            return try database.context.fetch(fetchRequest).compactMap { convertToDocumentData($0) }
        } catch {
            print("Error fetching documents: \(error)")
            return []
        }
    }

    func getPendingSync() -> [DocumentData] {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "syncStatus == %@", "pending")

        do {
            return try database.context.fetch(fetchRequest).compactMap { convertToDocumentData($0) }
        } catch {
            print("Error fetching pending documents: \(error)")
            return []
        }
    }

    /// Scans stored before model v3 carry the old learner id in `userId`. The store
    /// only ever holds the signed-in user's data (logout clears it), so assign them to that user.
    func adoptDocuments(userId: String) throws -> Int {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "userId != %@", userId)
        let results = try database.context.fetch(fetchRequest)
        for object in results {
            object.setValue(userId, forKey: "userId")
        }
        if !results.isEmpty {
            try database.saveContext()
        }
        return results.count
    }

    func update(_ document: DocumentData) throws {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", document.id)

        if let entity = try database.context.fetch(fetchRequest).first {
            apply(document, to: entity)
            try database.saveContext()
        }
    }

    func delete(_ id: String) throws {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", id)

        for object in try database.context.fetch(fetchRequest) {
            database.context.delete(object)
        }
        try database.saveContext()
    }

    func deleteAll() throws {
        let fetchRequest = NSFetchRequest<NSFetchRequestResult>(entityName: "DocumentEntity")
        let deleteRequest = NSBatchDeleteRequest(fetchRequest: fetchRequest)

        try database.context.execute(deleteRequest)
        try database.saveContext()
    }

    private func apply(_ document: DocumentData, to entity: NSManagedObject) {
        entity.setValue(document.title, forKey: "title")
        entity.setValue(document.type, forKey: "type")
        entity.setValue(document.pageCount, forKey: "pageCount")
        entity.setValue(document.thumbnailPath, forKey: "thumbnailPath")
        entity.setValue(document.status, forKey: "status")
        entity.setValue(document.processingProgress, forKey: "processingProgress")
        entity.setValue(document.createdAt, forKey: "createdAt")
        entity.setValue(document.updatedAt, forKey: "updatedAt")
        entity.setValue(document.syncStatus, forKey: "syncStatus")
        entity.setValue(document.lastSyncedAt, forKey: "lastSyncedAt")
        entity.setValue(document.isDeleted, forKey: "deletedFlag")
        entity.setValue(document.deletedAt, forKey: "deletedAt")
    }

    private func convertToDocumentData(_ object: NSManagedObject) -> DocumentData? {
        guard let id = object.value(forKey: "id") as? String,
              let userId = object.value(forKey: "userId") as? String,
              let title = object.value(forKey: "title") as? String,
              let type = object.value(forKey: "type") as? String,
              let pageCount = object.value(forKey: "pageCount") as? Int,
              let status = object.value(forKey: "status") as? String,
              let processingProgress = object.value(forKey: "processingProgress") as? Int,
              let createdAt = object.value(forKey: "createdAt") as? String,
              let updatedAt = object.value(forKey: "updatedAt") as? String,
              let syncStatus = object.value(forKey: "syncStatus") as? String,
              let isDeleted = object.value(forKey: "deletedFlag") as? Bool else {
            return nil
        }

        return DocumentData(
            id: id,
            userId: userId,
            title: title,
            type: type,
            pageCount: pageCount,
            thumbnailPath: object.value(forKey: "thumbnailPath") as? String,
            status: status,
            processingProgress: processingProgress,
            createdAt: createdAt,
            updatedAt: updatedAt,
            syncStatus: syncStatus,
            lastSyncedAt: object.value(forKey: "lastSyncedAt") as? String,
            isDeleted: isDeleted,
            deletedAt: object.value(forKey: "deletedAt") as? String
        )
    }
}

// MARK: - Document Page Repository Implementation
class DocumentPageRepositoryImpl: DocumentPageRepository {
    private let database: WizlyDatabase

    init(database: WizlyDatabase) {
        self.database = database
    }

    func insert(_ page: DocumentPageData) throws -> Int64 {
        let context = database.context
        let entity = NSEntityDescription.insertNewObject(forEntityName: "DocumentPageEntity", into: context)

        entity.setValue(page.id, forKey: "id")
        entity.setValue(page.documentId, forKey: "documentId")
        entity.setValue(page.pageNumber, forKey: "pageNumber")
        entity.setValue(page.imagePath, forKey: "imagePath")
        entity.setValue(page.thumbnailPath, forKey: "thumbnailPath")
        entity.setValue(page.width, forKey: "width")
        entity.setValue(page.height, forKey: "height")
        entity.setValue(page.fileSize, forKey: "fileSize")
        entity.setValue(page.uploadedAt, forKey: "uploadedAt")
        entity.setValue(page.syncStatus, forKey: "syncStatus")
        entity.setValue(page.lastSyncedAt, forKey: "lastSyncedAt")
        entity.setValue(page.isDeleted, forKey: "deletedFlag")
        entity.setValue(page.deletedAt, forKey: "deletedAt")

        try database.saveContext()
        return 1
    }

    func getById(_ id: String) -> DocumentPageData? {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentPageEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", id)

        do {
            let results = try database.context.fetch(fetchRequest)
            return results.first.flatMap { convertToDocumentPageData($0) }
        } catch {
            print("Error fetching document page: \(error)")
            return nil
        }
    }

    func getByDocumentId(_ documentId: String) -> [DocumentPageData] {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentPageEntity")
        fetchRequest.predicate = NSPredicate(format: "documentId == %@ AND deletedFlag == NO", documentId)
        fetchRequest.sortDescriptors = [NSSortDescriptor(key: "pageNumber", ascending: true)]

        do {
            let results = try database.context.fetch(fetchRequest)
            return results.compactMap { convertToDocumentPageData($0) }
        } catch {
            print("Error fetching document pages: \(error)")
            return []
        }
    }

    func update(_ page: DocumentPageData) throws {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentPageEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", page.id)

        let results = try database.context.fetch(fetchRequest)
        if let entity = results.first {
            entity.setValue(page.pageNumber, forKey: "pageNumber")
            entity.setValue(page.imagePath, forKey: "imagePath")
            entity.setValue(page.thumbnailPath, forKey: "thumbnailPath")
            entity.setValue(page.width, forKey: "width")
            entity.setValue(page.height, forKey: "height")
            entity.setValue(page.fileSize, forKey: "fileSize")
            entity.setValue(page.uploadedAt, forKey: "uploadedAt")
            entity.setValue(page.syncStatus, forKey: "syncStatus")
            entity.setValue(page.lastSyncedAt, forKey: "lastSyncedAt")
            entity.setValue(page.isDeleted, forKey: "deletedFlag")
            entity.setValue(page.deletedAt, forKey: "deletedAt")

            try database.saveContext()
        }
    }

    func delete(_ id: String) throws {
        let fetchRequest = NSFetchRequest<NSManagedObject>(entityName: "DocumentPageEntity")
        fetchRequest.predicate = NSPredicate(format: "id == %@", id)

        let results = try database.context.fetch(fetchRequest)
        for object in results {
            database.context.delete(object)
        }

        try database.saveContext()
    }

    func deleteAll() throws {
        let fetchRequest = NSFetchRequest<NSFetchRequestResult>(entityName: "DocumentPageEntity")
        let deleteRequest = NSBatchDeleteRequest(fetchRequest: fetchRequest)

        try database.context.execute(deleteRequest)
        try database.saveContext()
    }

    private func convertToDocumentPageData(_ object: NSManagedObject) -> DocumentPageData? {
        guard let id = object.value(forKey: "id") as? String,
              let documentId = object.value(forKey: "documentId") as? String,
              let pageNumber = object.value(forKey: "pageNumber") as? Int,
              let imagePath = object.value(forKey: "imagePath") as? String,
              let uploadedAt = object.value(forKey: "uploadedAt") as? String,
              let syncStatus = object.value(forKey: "syncStatus") as? String,
              let isDeleted = object.value(forKey: "deletedFlag") as? Bool else {
            return nil
        }

        return DocumentPageData(
            id: id,
            documentId: documentId,
            pageNumber: pageNumber,
            imagePath: imagePath,
            thumbnailPath: object.value(forKey: "thumbnailPath") as? String,
            width: object.value(forKey: "width") as? Int,
            height: object.value(forKey: "height") as? Int,
            fileSize: object.value(forKey: "fileSize") as? Int64,
            uploadedAt: uploadedAt,
            syncStatus: syncStatus,
            lastSyncedAt: object.value(forKey: "lastSyncedAt") as? String,
            isDeleted: isDeleted,
            deletedAt: object.value(forKey: "deletedAt") as? String
        )
    }
}
