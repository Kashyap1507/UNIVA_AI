import Foundation
import BackgroundTasks
@preconcurrency import shared

/**
 * Background sync handler for iOS
 * Equivalent to Android's DocumentSyncWorker
 *
 * Features:
 * - Runs every 15 minutes when device is online
 * - Syncs pending documents to backend
 * - Respects battery and network constraints
 *
 * Usage:
 * 1. Register task in Info.plist: BGTaskSchedulerPermittedIdentifiers
 * 2. Call IOSBackgroundSyncHandler.register() in AppDelegate
 * 3. Call IOSBackgroundSyncHandler.schedule() after registration
 */
@objc public class IOSBackgroundSyncHandler: NSObject {

    // Background task identifier (must match Info.plist)
    private static let taskIdentifier = "com.kashyap.wizlyai.documentsync"

    /**
     * Register background task handler
     * Call this in AppDelegate.didFinishLaunchingWithOptions
     */
    @objc public static func register() {
        print("📅 iOS: Registering background document sync task")

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            print("🔄 iOS: Background sync task started")
            self.handleBackgroundSync(task: task as! BGAppRefreshTask)
        }

        print("✅ iOS: Background sync task registered")
    }

    /**
     * Schedule next background sync
     * This should be called:
     * - After registration
     * - At the end of each background sync execution
     * - When app enters background
     *
     * Constraints:
     * - Runs earliest in 15 minutes
     * - Requires network connectivity (iOS manages this automatically)
     * - Won't run when device is in Low Power Mode (iOS manages this)
     */
    @objc public static func schedule() {
        print("📅 iOS: Scheduling background document sync")

        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)

        // Schedule to run in 15 minutes (iOS may defer further based on system conditions)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // 15 minutes

        // iOS automatically manages:
        // - Network availability (won't run offline)
        // - Battery state (won't run in Low Power Mode)
        // - System load (may defer if device is busy)
        // - User charging state (prioritizes when charging)

        do {
            try BGTaskScheduler.shared.submit(request)
            print("✅ iOS: Background sync scheduled for 15 minutes from now")
            print("   Note: iOS may defer based on battery, network, and system conditions")
        } catch {
            print("❌ iOS: Failed to schedule background sync")

            // Log specific error cases
            // Note: Avoid using error.localizedDescription as it causes dispatch queue assertion failure
            if let bgError = error as? BGTaskScheduler.Error {
                switch bgError.code {
                case .unavailable:
                    print("   Reason: Background task scheduler unavailable (expected on simulator)")
                case .tooManyPendingTaskRequests:
                    print("   Reason: Too many pending task requests")
                case .notPermitted:
                    print("   Reason: Not permitted (check Info.plist BGTaskSchedulerPermittedIdentifiers)")
                @unknown default:
                    print("   Reason: Unknown error code \(bgError.code.rawValue)")
                }
            } else {
                // For non-BGTaskScheduler errors, it's safe to use String(describing:)
                print("   Error: \(String(describing: error))")
            }
        }
    }

    /**
     * Handle background sync execution
     * This is called by iOS when it's time to sync
     *
     * Process:
     * 1. Schedule next sync (15 minutes)
     * 2. Check network conditions (WiFi vs Cellular)
     * 3. Sync pending documents via DocumentSyncManager
     * 4. Report success/failure to iOS
     */
    private static func handleBackgroundSync(task: BGAppRefreshTask) {
        let startTime = Date()
        print("═══════════════════════════════════════════════════════")
        print("📦 iOS: Starting background document sync")
        print("   Time: \(startTime)")
        print("═══════════════════════════════════════════════════════")

        // Schedule next sync immediately (iOS best practice)
        schedule()

        // Set expiration handler for iOS-enforced time limit (~30 seconds)
        task.expirationHandler = {
            let duration = Date().timeIntervalSince(startTime)
            print("⚠️ iOS: Background sync expired after \(String(format: "%.2f", duration))s")
            print("   iOS terminated the task - will retry on next schedule")
            print("═══════════════════════════════════════════════════════")
            task.setTaskCompleted(success: false)
        }

        // Perform sync in background
        // Note: BGAppRefreshTask is thread-safe and designed to be used across contexts
        // We use @unchecked Sendable wrapper to satisfy Swift 6 concurrency requirements
        final class TaskWrapper: @unchecked Sendable {
            let task: BGAppRefreshTask
            init(_ task: BGAppRefreshTask) {
                self.task = task
            }
        }
        let taskWrapper = TaskWrapper(task)

        Task {
            do {
                // 1. Sync app settings first (lightweight operation)
                print("⚙️ iOS: Syncing app settings")
                let settingsStats = try await IOSAppSettingsSyncManager.shared.syncPendingSettings()
                print("   Settings synced: \(settingsStats.settingsSynced)")

                // 2. Sync pending documents (main operation)
                print("📤 iOS: Calling DocumentSyncManager.syncPendingDocuments()")

                // Create DocumentSyncManager instance
                let syncManager = DocumentSyncManager()

                // Sync pending documents (checks network conditions internally)
                // Using nonisolated context to avoid Swift 6 sendability issues with KMP types
                let result = try await syncManager.syncPendingDocuments()

                // Extract synced count from result
                let syncedCount: Int32
                if let kotlinInt = result as? KotlinInt {
                    syncedCount = kotlinInt.int32Value
                } else if let intValue = result as? Int32 {
                    syncedCount = intValue
                } else if let intValue = result as? Int {
                    syncedCount = Int32(intValue)
                } else {
                    syncedCount = 0
                }

                let duration = Date().timeIntervalSince(startTime)

                // Since try await succeeded, sync was successful
                print("✅ iOS: Background sync completed successfully")
                print("   Settings synced: \(settingsStats.settingsSynced)")
                print("   Documents synced: \(syncedCount)")
                print("   Duration: \(String(format: "%.2f", duration))s")
                print("═══════════════════════════════════════════════════════")
                taskWrapper.task.setTaskCompleted(success: true)

            } catch {
                let duration = Date().timeIntervalSince(startTime)
                print("❌ iOS: Background sync failed")
                print("   Error: \(error.localizedDescription)")
                print("   Duration: \(String(format: "%.2f", duration))s")
                print("═══════════════════════════════════════════════════════")
                taskWrapper.task.setTaskCompleted(success: false)
            }
        }
    }

    /**
     * Cancel scheduled background sync
     * Call this on logout
     */
    @objc public static func cancel() {
        print("🛑 iOS: Cancelling background document sync")
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        print("✅ iOS: Background sync cancelled")
    }
}
