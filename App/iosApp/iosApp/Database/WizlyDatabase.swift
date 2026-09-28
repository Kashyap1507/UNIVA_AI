import Foundation
@preconcurrency import CoreData

/**
 * CoreData database manager for Wizly app with offline-first support
 * Equivalent to Android's WizlyDatabase using Room
 */
@MainActor
class WizlyDatabase {

    // MARK: - Singleton

    static let shared = WizlyDatabase()

    // MARK: - Core Data Stack

    lazy var persistentContainer: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "WizlyDataModel")

        // Lightweight migration between model versions (v2 adds the course cache entities;
        // v3 drops the K-12 entities and renames DocumentEntity.childId to userId)
        if let description = container.persistentStoreDescriptions.first {
            description.shouldMigrateStoreAutomatically = true
            description.shouldInferMappingModelAutomatically = true
        }

        container.loadPersistentStores { description, error in
            if let error = error {
                print("❌ CoreData: Failed to load persistent store: \(error.localizedDescription)")
                fatalError("Unresolved error \(error)")
            }
            print("✅ CoreData: Database created successfully at: \(description.url?.path ?? "unknown")")
        }

        // Enable automatic merging of changes from parent
        container.viewContext.automaticallyMergesChangesFromParent = true
        container.viewContext.mergePolicy = NSMergeByPropertyObjectTrumpMergePolicy

        print("🔧 CoreData: Database instance initialized")
        return container
    }()

    var context: NSManagedObjectContext {
        return persistentContainer.viewContext
    }

    // MARK: - Core Data Saving Support

    func saveContext() throws {
        let context = persistentContainer.viewContext
        if context.hasChanges {
            try context.save()
        }
    }

    // MARK: - Background Context

    func newBackgroundContext() -> NSManagedObjectContext {
        return persistentContainer.newBackgroundContext()
    }

    // MARK: - Clear All Data

    func clearAllData() throws {
        let entities = persistentContainer.managedObjectModel.entities

        for entity in entities {
            guard let entityName = entity.name else { continue }

            let fetchRequest = NSFetchRequest<NSFetchRequestResult>(entityName: entityName)
            let deleteRequest = NSBatchDeleteRequest(fetchRequest: fetchRequest)

            try persistentContainer.persistentStoreCoordinator.execute(
                deleteRequest,
                with: context
            )
        }

        try saveContext()
        print("🗑️ CoreData: All data cleared")
    }

    // MARK: - Repository Access

    lazy var documentRepository: DocumentRepository = DocumentRepositoryImpl(database: self)
    lazy var documentPageRepository: DocumentPageRepository = DocumentPageRepositoryImpl(database: self)
    lazy var userRepository: UserRepository = UserRepositoryImpl(database: self)

    // MARK: - Private Init

    private init() {
        print("🚀 CoreData: Initializing WizlyDatabase")
    }
}
