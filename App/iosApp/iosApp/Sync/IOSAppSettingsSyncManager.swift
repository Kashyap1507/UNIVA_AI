import Foundation
@preconcurrency import shared

/**
 * Temporary NetworkConstants until proper shared networking is implemented
 * TODO: Replace with shared Kotlin NetworkConstants
 */
@MainActor
private class NetworkConstants {
    static let shared = NetworkConstants()
    let API_BASE_URL = "http://localhost:5000"
}

/**
 * iOS App Settings Sync Manager
 *
 * Handles bidirectional synchronization of app settings between:
 * - Local NSUserDefaults (offline-first storage)
 * - Backend Firebase Firestore (cloud persistence via API)
 *
 * Features:
 * - Upload pending settings changes to backend
 * - Download settings from backend on app start
 * - Track sync status (pending/synced/failed)
 * - Retry failed uploads with exponential backoff
 * - Cache invalidation for UI refresh
 *
 * Settings synced:
 * - cloudSyncEnabled (bool) - Upload documents to cloud storage
 * - cellularSyncEnabled (bool) - Sync over cellular data
 * - scanQuality (string) - Document scan quality preset (SMALL/STANDARD/LARGE/HD)
 *
 * Usage:
 * ```swift
 * let syncManager = IOSAppSettingsSyncManager.shared
 *
 * // Upload pending settings changes
 * Task {
 *     let stats = try await syncManager.syncPendingSettings()
 *     print("Synced \(stats.settingsSynced) settings")
 * }
 * ```
 */
@MainActor
@objc public class IOSAppSettingsSyncManager: NSObject {

    // MARK: - Singleton

    @objc public static let shared = IOSAppSettingsSyncManager()

    // MARK: - Constants

    private let TAG = "IOSAppSettingsSyncManager"
    private let SETTINGS_ENDPOINT = "/api/v1/settings"

    // NSUserDefaults keys
    private let KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    private let KEY_CELLULAR_SYNC_ENABLED = "cellular_sync_enabled"
    private let KEY_SCAN_QUALITY = "scan_quality"
    private let KEY_SYNC_STATUS = "app_settings_sync_status"
    private let KEY_UPDATED_AT = "app_settings_updated_at"
    private let KEY_CREATED_AT = "app_settings_created_at"

    // MARK: - Properties

    private let userDefaults = UserDefaults.standard

    // MARK: - Private Init

    private override init() {
        super.init()
    }

    // MARK: - Public Methods

    /**
     * Sync pending app settings to backend
     *
     * This is the PRIMARY method for uploading settings changes.
     * It handles:
     * - Checking if settings have 'pending' sync status
     * - Uploading to backend via PUT /api/v1/settings
     * - Marking as 'synced' on success
     * - Marking as 'failed' for retry on error
     *
     * Called by:
     * - IOSBackgroundSyncHandler during periodic sync
     * - After settings change in UI
     * - On app foreground if pending changes exist
     *
     * @return SettingsSyncStats with sync results
     */
    @objc public func syncPendingSettings() async throws -> SettingsSyncStats {
        let startTime = Date()
        print("═══════════════════════════════════════════════════════")
        print("⚙️ \(TAG): Syncing pending app settings")
        print("═══════════════════════════════════════════════════════")

        // 1. Check if settings are pending sync
        let syncStatus = userDefaults.string(forKey: KEY_SYNC_STATUS) ?? "synced"

        if syncStatus != "pending" {
            print("ℹ️ \(TAG): No pending settings to sync (status: \(syncStatus))")
            print("═══════════════════════════════════════════════════════")
            return SettingsSyncStats(
                settingsSynced: 0,
                failures: 0,
                durationMs: Int64(Date().timeIntervalSince(startTime) * 1000)
            )
        }

        print("📤 \(TAG): Found pending settings to sync")

        var successCount = 0
        var failureCount = 0

        // 2. Mark as syncing
        userDefaults.set("syncing", forKey: KEY_SYNC_STATUS)
        userDefaults.set(ISO8601DateFormatter().string(from: Date()), forKey: KEY_UPDATED_AT)
        userDefaults.synchronize()

        // 3. Upload to backend
        do {
            try await uploadSettings()

            // Mark as synced
            userDefaults.set("synced", forKey: KEY_SYNC_STATUS)
            userDefaults.set(ISO8601DateFormatter().string(from: Date()), forKey: KEY_UPDATED_AT)
            userDefaults.synchronize()

            successCount = 1
            print("✅ \(TAG): Settings synced successfully")

        } catch {
            // Mark as failed for retry
            userDefaults.set("failed", forKey: KEY_SYNC_STATUS)
            userDefaults.set(ISO8601DateFormatter().string(from: Date()), forKey: KEY_UPDATED_AT)
            userDefaults.synchronize()

            failureCount = 1
            print("❌ \(TAG): Settings sync failed: \(error.localizedDescription)")
        }

        // 4. Invalidate caches
        invalidateSettingsCaches()

        // 5. Log statistics
        let duration = Date().timeIntervalSince(startTime) * 1000 // Convert to ms
        print("📊 \(TAG): Settings Sync Stats:")
        print("   - Settings synced: \(successCount)")
        print("   - Failures: \(failureCount)")
        print("   - Duration: \(String(format: "%.0f", duration))ms")
        print("═══════════════════════════════════════════════════════")

        return SettingsSyncStats(
            settingsSynced: Int32(successCount),
            failures: Int32(failureCount),
            durationMs: Int64(duration)
        )
    }

    /**
     * Upload settings to backend API
     *
     * Calls PUT /api/v1/settings with settings data.
     *
     * @throws Error if upload fails
     */
    private func uploadSettings() async throws {
        print("📤 \(TAG): Uploading settings to \(SETTINGS_ENDPOINT)")

        // Get current settings from UserDefaults
        let cloudSyncEnabled = userDefaults.bool(forKey: KEY_CLOUD_SYNC_ENABLED)
        let cellularSyncEnabled = userDefaults.bool(forKey: KEY_CELLULAR_SYNC_ENABLED)
        let scanQuality = userDefaults.string(forKey: KEY_SCAN_QUALITY) ?? "STANDARD"
        let createdAt = userDefaults.string(forKey: KEY_CREATED_AT) ?? ISO8601DateFormatter().string(from: Date())
        let updatedAt = ISO8601DateFormatter().string(from: Date())

        print("   cloudSyncEnabled: \(cloudSyncEnabled)")
        print("   cellularSyncEnabled: \(cellularSyncEnabled)")
        print("   scanQuality: \(scanQuality)")

        // Build request body
        let requestBody: [String: Any] = [
            "cloud_sync_enabled": cloudSyncEnabled,
            "cellular_sync_enabled": cellularSyncEnabled,
            "scan_quality": scanQuality,
            "created_at": createdAt,
            "updated_at": updatedAt
        ]

        // Make API call
        guard let url = URL(string: NetworkConstants.shared.API_BASE_URL + SETTINGS_ENDPOINT) else {
            throw NSError(domain: TAG, code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Add authorization header if available
        let token = PreferencesManager().getString(key: "auth_token", defaultValue: "")
        if !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        // Serialize request body
        request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)

        // Perform request
        let (_, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(domain: TAG, code: -2, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])
        }

        if httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 {
            print("✅ \(TAG): Settings uploaded successfully: \(httpResponse.statusCode)")
        } else {
            let errorMessage = "Upload failed: HTTP \(httpResponse.statusCode)"
            print("❌ \(TAG): \(errorMessage)")
            throw NSError(domain: TAG, code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: errorMessage])
        }
    }

    /**
     * Download settings from backend API
     *
     * Calls GET /api/v1/settings and saves to NSUserDefaults.
     * Used on app start and after authentication.
     *
     * @throws Error if download fails
     */
    @objc public func downloadSettings() async throws {
        print("📥 \(TAG): Downloading settings from backend")

        guard let url = URL(string: NetworkConstants.shared.API_BASE_URL + SETTINGS_ENDPOINT) else {
            throw NSError(domain: TAG, code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        // Add authorization header if available
        let token = PreferencesManager().getString(key: "auth_token", defaultValue: "")
        if !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        // Perform request
        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(domain: TAG, code: -2, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])
        }

        guard httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 else {
            let errorMessage = "Download failed: HTTP \(httpResponse.statusCode)"
            print("❌ \(TAG): \(errorMessage)")
            throw NSError(domain: TAG, code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: errorMessage])
        }

        // Parse response
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: TAG, code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON response"])
        }

        // Extract settings
        let cloudSyncEnabled = json["cloud_sync_enabled"] as? Bool ?? true
        let cellularSyncEnabled = json["cellular_sync_enabled"] as? Bool ?? false
        let scanQuality = json["scan_quality"] as? String ?? "STANDARD"
        let createdAt = json["created_at"] as? String ?? ISO8601DateFormatter().string(from: Date())
        let updatedAt = json["updated_at"] as? String ?? ISO8601DateFormatter().string(from: Date())

        // Save to UserDefaults
        userDefaults.set(cloudSyncEnabled, forKey: KEY_CLOUD_SYNC_ENABLED)
        userDefaults.set(cellularSyncEnabled, forKey: KEY_CELLULAR_SYNC_ENABLED)
        userDefaults.set(scanQuality, forKey: KEY_SCAN_QUALITY)
        userDefaults.set(createdAt, forKey: KEY_CREATED_AT)
        userDefaults.set(updatedAt, forKey: KEY_UPDATED_AT)
        userDefaults.set("synced", forKey: KEY_SYNC_STATUS)
        userDefaults.synchronize()

        print("✅ \(TAG): Settings downloaded and saved")
        print("   cloudSyncEnabled: \(cloudSyncEnabled)")
        print("   cellularSyncEnabled: \(cellularSyncEnabled)")
        print("   scanQuality: \(scanQuality)")

        // Invalidate caches
        invalidateSettingsCaches()
    }

    /**
     * Mark settings as pending sync
     *
     * Call this after changing any setting in the UI.
     */
    @objc public func markAsPending() {
        userDefaults.set("pending", forKey: KEY_SYNC_STATUS)
        userDefaults.set(ISO8601DateFormatter().string(from: Date()), forKey: KEY_UPDATED_AT)
        userDefaults.synchronize()

        print("⏳ \(TAG): Settings marked as pending sync")
    }

    /**
     * Get pending sync count
     *
     * @return 1 if settings are pending sync, 0 otherwise
     */
    @objc public func getPendingSyncCount() -> Int {
        let syncStatus = userDefaults.string(forKey: KEY_SYNC_STATUS) ?? "synced"
        return syncStatus == "pending" ? 1 : 0
    }

    /**
     * Get current cellular sync enabled setting
     *
     * @return True if cellular sync is enabled
     */
    @objc public func getCellularSyncEnabled() -> Bool {
        return userDefaults.bool(forKey: KEY_CELLULAR_SYNC_ENABLED)
    }

    /**
     * Set cellular sync enabled setting
     *
     * @param enabled True to enable cellular sync
     */
    @objc public func setCellularSyncEnabled(_ enabled: Bool) {
        userDefaults.set(enabled, forKey: KEY_CELLULAR_SYNC_ENABLED)
        markAsPending()
    }

    /**
     * Get current cloud sync enabled setting
     *
     * @return True if cloud sync is enabled
     */
    @objc public func getCloudSyncEnabled() -> Bool {
        return userDefaults.bool(forKey: KEY_CLOUD_SYNC_ENABLED)
    }

    /**
     * Set cloud sync enabled setting
     *
     * @param enabled True to enable cloud sync
     */
    @objc public func setCloudSyncEnabled(_ enabled: Bool) {
        userDefaults.set(enabled, forKey: KEY_CLOUD_SYNC_ENABLED)
        markAsPending()
    }

    /**
     * Get current scan quality setting
     *
     * @return Scan quality (SMALL/STANDARD/LARGE/HD)
     */
    @objc public func getScanQuality() -> String {
        return userDefaults.string(forKey: KEY_SCAN_QUALITY) ?? "STANDARD"
    }

    /**
     * Set scan quality setting
     *
     * @param quality Scan quality (SMALL/STANDARD/LARGE/HD)
     */
    @objc public func setScanQuality(_ quality: String) {
        userDefaults.set(quality, forKey: KEY_SCAN_QUALITY)
        markAsPending()
    }

    // MARK: - Private Methods

    /**
     * Invalidate settings-related caches
     *
     * This forces UI to refresh and fetch new data.
     *
     * TODO: Implement SimpleCache or use shared Kotlin SimpleCache when available
     */
    private func invalidateSettingsCaches() {
        // TODO: Uncomment when SimpleCache is available from shared framework
        // SimpleCache.companion.invalidatePattern(pattern: "app_settings")
        // SimpleCache.companion.invalidatePattern(pattern: "settings")
        print("🗑️ \(TAG): Settings caches invalidated (cache invalidation not yet implemented)")
    }
}
