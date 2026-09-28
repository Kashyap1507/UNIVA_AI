import Foundation
import shared

/**
 * Result of bootstrap sync operation
 */
struct SyncResult {
    let itemsSynced: Int
    let syncTimestamp: String
    let duration: TimeInterval
}

/**
 * Processes the bootstrap payload from the auth response on iOS.
 *
 * Bootstrap carries the sync timestamp, app settings and subscription usage;
 * courses, deadlines and cards are fetched by their own repositories, and
 * scans are owned by the signed-in user. iOS equivalent of Android's SyncRepository.
 */
@MainActor
class IOSBootstrapSyncManager {

    // MARK: - Properties

    private let database: WizlyDatabase
    private let syncPrefs: IOSSyncPreferences

    // MARK: - Initialization

    init() {
        self.database = WizlyDatabase.shared
        self.syncPrefs = IOSSyncPreferences()
    }

    // MARK: - Process Bootstrap Data

    /**
     * Process bootstrap data from login/signup response
     *
     * @param bootstrapData Bootstrap data from auth response
     * @param userId Current user's ID (from auth response)
     * @return Result with sync statistics
     */
    func processBootstrapData(
        bootstrapData: BootstrapData,
        userId: String
    ) async throws -> SyncResult {
        let startTime = Date()
        var itemsSynced = 0

        print("🔄 iOS: bootstrap sync for user \(userId) (timestamp \(bootstrapData.syncTimestamp))")

        // Scans saved before Phase 13 were keyed by the old learner record; they belong to this user
        let adopted = try database.documentRepository.adoptDocuments(userId: userId)
        if adopted > 0 {
            print("   📄 Assigned \(adopted) older scans to the signed-in user")
            itemsSynced += adopted
        }

        // Save sync timestamp for future smart sync
        syncPrefs.saveLastSyncTimestamp(bootstrapData.syncTimestamp)

        let duration = Date().timeIntervalSince(startTime)
        print("✅ iOS: bootstrap sync completed in \(Int(duration * 1000))ms")

        return SyncResult(
            itemsSynced: itemsSynced,
            syncTimestamp: bootstrapData.syncTimestamp,
            duration: duration
        )
    }

    // MARK: - Should Sync

    /**
     * Check if sync is needed (24-hour interval)
     * @return true if sync needed
     */
    func shouldSync() -> Bool {
        return syncPrefs.shouldSync(intervalHours: 24)
    }
}
