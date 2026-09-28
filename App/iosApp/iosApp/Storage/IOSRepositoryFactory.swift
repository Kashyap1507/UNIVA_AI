import Foundation
import shared

/**
 * Factory for creating repository and storage instances on iOS
 * Provides easy access to platform-specific implementations
 */
@MainActor
class IOSRepositoryFactory {

    // MARK: - Singleton

    static let shared = IOSRepositoryFactory()

    // MARK: - Properties

    private let localStorage: IOSLocalDocumentStorage
    private let documentProvider: IOSDocumentProvider

    // MARK: - Initialization

    private init() {
        self.localStorage = IOSLocalDocumentStorage()
        self.documentProvider = IOSDocumentProvider()
        print("🏭 IOSRepositoryFactory: Initialized")
    }

    // MARK: - Factory Methods

    /**
     * Get LocalDocumentStorage instance for iOS
     */
    func getLocalDocumentStorage() -> IOSLocalDocumentStorage {
        print("✅ IOSRepositoryFactory: Providing LocalDocumentStorage")
        return localStorage
    }

    /**
     * Get DocumentProvider instance for iOS
     */
    func getDocumentProvider() -> IOSDocumentProvider {
        print("✅ IOSRepositoryFactory: Providing DocumentProvider")
        return documentProvider
    }

    /**
     * Get WizlyDatabase instance
     */
    func getDatabase() -> WizlyDatabase {
        return WizlyDatabase.shared
    }
}
