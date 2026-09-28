import Foundation

/**
 * iOS Sync Preferences using UserDefaults
 * Tracks sync timestamps and intervals for bootstrap sync
 */
class IOSSyncPreferences {

    // MARK: - Keys

    private let LAST_SYNC_TIMESTAMP_KEY = "last_sync_timestamp"
    private let LAST_SYNC_TIME_KEY = "last_sync_time"

    // MARK: - UserDefaults

    private let defaults = UserDefaults.standard

    // MARK: - Save Last Sync Timestamp

    func saveLastSyncTimestamp(_ timestamp: String) {
        defaults.set(timestamp, forKey: LAST_SYNC_TIMESTAMP_KEY)
        defaults.set(Date().timeIntervalSince1970, forKey: LAST_SYNC_TIME_KEY)
        defaults.synchronize()
        print("💾 SyncPrefs: Saved sync timestamp: \(timestamp)")
    }

    // MARK: - Get Last Sync Timestamp

    func getLastSyncTimestamp() -> String? {
        return defaults.string(forKey: LAST_SYNC_TIMESTAMP_KEY)
    }

    // MARK: - Should Sync

    /**
     * Check if sync is needed based on interval
     * @param intervalHours Sync interval in hours (default: 24)
     * @return true if sync is needed
     */
    func shouldSync(intervalHours: Int = 24) -> Bool {
        guard let lastSyncTime = defaults.object(forKey: LAST_SYNC_TIME_KEY) as? TimeInterval else {
            print("📊 SyncPrefs: No previous sync found, sync needed")
            return true
        }

        let now = Date().timeIntervalSince1970
        let elapsed = now - lastSyncTime
        let intervalSeconds = TimeInterval(intervalHours * 3600)

        let shouldSync = elapsed >= intervalSeconds

        if shouldSync {
            print("📊 SyncPrefs: Sync needed (elapsed: \(Int(elapsed))s, interval: \(Int(intervalSeconds))s)")
        } else {
            print("📊 SyncPrefs: Sync not needed (elapsed: \(Int(elapsed))s, interval: \(Int(intervalSeconds))s)")
        }

        return shouldSync
    }

    // MARK: - Clear

    func clear() {
        defaults.removeObject(forKey: LAST_SYNC_TIMESTAMP_KEY)
        defaults.removeObject(forKey: LAST_SYNC_TIME_KEY)
        defaults.synchronize()
        print("🗑️ SyncPrefs: Cleared sync preferences")
    }
}
