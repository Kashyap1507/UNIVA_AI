import Foundation
import shared

/**
 * Bridge to expose IOSBootstrapSyncManager to Kotlin code
 * This is called from BootstrapSyncCoordinator.ios.kt
 */
@MainActor
@objc public class IOSBootstrapSyncBridge: NSObject {

    @objc public static let shared = IOSBootstrapSyncBridge()

    private let syncManager: IOSBootstrapSyncManager

    private override init() {
        self.syncManager = IOSBootstrapSyncManager()
        super.init()
    }

    /**
     * Process bootstrap data after authentication
     * Called from Kotlin BootstrapSyncCoordinator
     */
    @objc public func syncBootstrapData(
        bootstrapData: BootstrapData,
        userId: String
    ) {
        print("═══════════════════════════════════════════════════════")
        print("🔵 IOSBootstrapSyncBridge: syncBootstrapData called from Kotlin")
        print("═══════════════════════════════════════════════════════")
        print("📊 Bootstrap Data Received:")
        print("   User ID: \(userId)")
        print("   Sync Timestamp: \(bootstrapData.syncTimestamp)")
        print("───────────────────────────────────────────────────────")

        Task {
            do {
                print("\n🚀 Starting async sync task...")
                let result = try await syncManager.processBootstrapData(
                    bootstrapData: bootstrapData,
                    userId: userId
                )

                print("\n═══════════════════════════════════════════════════════")
                print("✅ IOSBootstrapSyncBridge: Sync completed successfully")
                print("═══════════════════════════════════════════════════════")
                print("   Items synced: \(result.itemsSynced)")
                print("   Duration: \(Int(result.duration * 1000))ms")
                print("   Timestamp: \(result.syncTimestamp)")
                print("═══════════════════════════════════════════════════════")
            } catch {
                print("\n═══════════════════════════════════════════════════════")
                print("❌ IOSBootstrapSyncBridge: Sync FAILED")
                print("═══════════════════════════════════════════════════════")
                print("   Error: \(error.localizedDescription)")
                print("   Error type: \(type(of: error))")
                if let nsError = error as NSError? {
                    print("   Domain: \(nsError.domain)")
                    print("   Code: \(nsError.code)")
                    print("   UserInfo: \(nsError.userInfo)")
                }
                print("═══════════════════════════════════════════════════════")
            }
        }
    }

    /**
     * Clear all synced data (called on logout)
     */
    @objc public func clearSyncData() {
        print("🔵 IOSBootstrapSyncBridge: clearSyncData called from Kotlin")

        do {
            try WizlyDatabase.shared.clearAllData()
            print("✅ IOSBootstrapSyncBridge: All data cleared")
        } catch {
            print("❌ IOSBootstrapSyncBridge: Failed to clear data: \(error.localizedDescription)")
        }
    }

    /**
     * Check if sync is needed based on 24-hour interval
     */
    @objc public func shouldSync() -> Bool {
        return syncManager.shouldSync()
    }

    /**
     * Trigger smart sync to check for updates
     * Called from FCM notifications or manual refresh
     */
    @objc public func smartSync(force: Bool) {
        print("🔵 IOSBootstrapSyncBridge: smartSync called from Kotlin (force: \(force))")

        // Check if sync is needed (unless forced)
        if !force && !syncManager.shouldSync() {
            print("⏭️ IOSBootstrapSyncBridge: Skipping sync (last sync was recent)")
            return
        }

        Task {
            do {
                print("🔄 IOSBootstrapSyncBridge: Starting smart sync...")

                // TODO: Fetch sync metadata from backend
                // This would:
                // 1. Call /api/sync/metadata to get timestamps of changed entities
                // 2. Compare with local timestamps
                // 3. Fetch only changed entities
                // 4. Update local CoreData

                // For now, log that we attempted smart sync
                print("✅ IOSBootstrapSyncBridge: Smart sync check complete")
                print("   Note: Full implementation pending - requires backend sync metadata endpoint")

            } catch {
                print("❌ IOSBootstrapSyncBridge: Smart sync failed: \(error.localizedDescription)")
            }
        }
    }
}
