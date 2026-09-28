import Foundation
@preconcurrency import CoreData
import shared

/**
 * CoreData implementation of the shared CourseCacheStore (model version 2:
 * CachedCourse / CachedCourseMaterial). Rows keep the JSON payload from the
 * API. Called from Kotlin background threads, so every call uses its own
 * background context and blocks until done.
 */
final class IOSCourseCacheStore: NSObject, CourseCacheStore {
    private let container: NSPersistentContainer

    init(container: NSPersistentContainer) {
        self.container = container
        super.init()
    }

    // MARK: - Courses

    func saveCourses(userId: String, rows: [CacheRow]) {
        perform { context in
            self.deleteAll(entity: "CachedCourse", predicate: NSPredicate(format: "userId == %@", userId), in: context)
            let now = Date()
            for row in rows {
                let object = NSEntityDescription.insertNewObject(forEntityName: "CachedCourse", into: context)
                object.setValue(row.id, forKey: "id")
                object.setValue(userId, forKey: "userId")
                object.setValue(row.json, forKey: "payloadJson")
                object.setValue(row.updatedAt, forKey: "updatedAt")
                object.setValue(now, forKey: "cachedAt")
            }
        }
    }

    func loadCourses(userId: String) -> [CacheRow] {
        fetch(entity: "CachedCourse", predicate: NSPredicate(format: "userId == %@", userId))
    }

    // MARK: - Materials

    func saveMaterials(courseId: String, rows: [CacheRow]) {
        perform { context in
            self.deleteAll(entity: "CachedCourseMaterial", predicate: NSPredicate(format: "courseId == %@", courseId), in: context)
            let now = Date()
            for row in rows {
                let object = NSEntityDescription.insertNewObject(forEntityName: "CachedCourseMaterial", into: context)
                object.setValue(row.id, forKey: "id")
                object.setValue(courseId, forKey: "courseId")
                object.setValue(row.json, forKey: "payloadJson")
                object.setValue(row.updatedAt, forKey: "updatedAt")
                object.setValue(now, forKey: "cachedAt")
            }
        }
    }

    func loadMaterials(courseId: String) -> [CacheRow] {
        fetch(entity: "CachedCourseMaterial", predicate: NSPredicate(format: "courseId == %@", courseId))
    }

    // MARK: - Deletion

    func deleteCourse(courseId: String) {
        perform { context in
            self.deleteAll(entity: "CachedCourseMaterial", predicate: NSPredicate(format: "courseId == %@", courseId), in: context)
            self.deleteAll(entity: "CachedCourse", predicate: NSPredicate(format: "id == %@", courseId), in: context)
        }
    }

    func clear() {
        perform { context in
            self.deleteAll(entity: "CachedCourseMaterial", predicate: nil, in: context)
            self.deleteAll(entity: "CachedCourse", predicate: nil, in: context)
        }
    }

    // MARK: - Helpers

    private func perform(_ block: @escaping (NSManagedObjectContext) -> Void) {
        let context = container.newBackgroundContext()
        context.performAndWait {
            block(context)
            do {
                if context.hasChanges { try context.save() }
            } catch {
                print("❌ IOSCourseCacheStore: save failed: \(error.localizedDescription)")
                context.rollback()
            }
        }
    }

    private func deleteAll(entity: String, predicate: NSPredicate?, in context: NSManagedObjectContext) {
        let request = NSFetchRequest<NSManagedObject>(entityName: entity)
        request.predicate = predicate
        (try? context.fetch(request))?.forEach { context.delete($0) }
    }

    private func fetch(entity: String, predicate: NSPredicate) -> [CacheRow] {
        var rows: [CacheRow] = []
        let context = container.newBackgroundContext()
        context.performAndWait {
            let request = NSFetchRequest<NSManagedObject>(entityName: entity)
            request.predicate = predicate
            let objects = (try? context.fetch(request)) ?? []
            rows = objects.compactMap { object in
                guard let id = object.value(forKey: "id") as? String,
                      let json = object.value(forKey: "payloadJson") as? String else { return nil }
                return CacheRow(id: id, json: json, updatedAt: object.value(forKey: "updatedAt") as? String)
            }
        }
        return rows
    }
}
