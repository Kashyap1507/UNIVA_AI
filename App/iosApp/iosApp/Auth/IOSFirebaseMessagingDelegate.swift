import Foundation
import FirebaseMessaging
import UserNotifications
@preconcurrency import shared

/**
 * Firebase Cloud Messaging delegate for iOS
 * Handles incoming push notifications and FCM token refresh
 */
@MainActor
class IOSFirebaseMessagingDelegate: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {

    // MARK: - Properties

    static let shared = IOSFirebaseMessagingDelegate()

    // MARK: - Setup

    func setup() {
        // Set messaging delegate
        Messaging.messaging().delegate = self

        // Set notification center delegate
        UNUserNotificationCenter.current().delegate = self

        // Request notification permissions
        requestNotificationPermissions()

        print("✅ IOSFirebaseMessagingDelegate: Setup complete")
    }

    // MARK: - Get and Register Current Token

    func getCurrentTokenAndRegister() {
        Messaging.messaging().token { [weak self] token, error in
            guard let self = self else { return }

            if let error = error {
                print("❌ Error fetching FCM token: \(error.localizedDescription)")
                return
            }

            guard let token = token else {
                print("⚠️ FCM token is nil")
                return
            }

            print("📲 Current FCM token retrieved: \(String(token.prefix(20)))...")

            // Register token with backend
            self.registerTokenWithBackend(token: token)
        }
    }

    // MARK: - Request Permissions

    /// Request notification permissions from the user
    /// Must be nonisolated so the completion handler doesn't inherit @MainActor isolation
    /// (iOS calls the completion handler from a background queue)
    nonisolated private func requestNotificationPermissions() {
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(
            options: authOptions
        ) { granted, error in
            // Closure is nonisolated - hop to MainActor for UI operations
            Task { @MainActor in
                if let error = error {
                    print("❌ Notification permission error: \(String(describing: error))")
                    return
                }

                if granted {
                    print("✅ Notification permissions granted")
                    UIApplication.shared.registerForRemoteNotifications()
                } else {
                    print("⚠️ Notification permissions denied")
                }
            }
        }
    }

    // MARK: - MessagingDelegate

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else {
            print("⚠️ FCM token is nil")
            return
        }

        print("🔑 FCM token received: \(token)")

        // Register token with backend on main actor
        Task { @MainActor in
            self.registerTokenWithBackend(token: token)
        }
    }

    // MARK: - Register Token with Backend

    // Fire-and-forget token registration
    // Uses helper class to avoid Swift 6 sendability issues with KMP async calls
    private func registerTokenWithBackend(token: String) {
        let deviceId = getOrCreateDeviceId()
        let deviceName = UIDevice.current.name
        let appVersion = getAppVersion()

        print("📲 Registering FCM token with backend")
        print("   Device ID: \(deviceId)")
        print("   Device: \(deviceName)")
        print("   Version: \(appVersion)")

        // Use helper to register token, avoiding sendability issues
        FCMTokenRegistrationHelper.shared.register(
            deviceId: deviceId,
            token: token,
            deviceName: deviceName,
            appVersion: appVersion
        )
    }

    // MARK: - UNUserNotificationCenterDelegate

    // Called when notification is received while app is in foreground
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo

        print("📬 Notification received in foreground")
        print("   User Info: \(userInfo)")

        // Handle data payload
        if let changeType = userInfo["type"] as? String {
            print("🎯 Routing to RealTimeDataSyncManager")
            // Route to RealTimeDataSyncManager for selective data refresh
            var dataDict: [String: String] = [:]
            for (key, value) in userInfo {
                if let stringKey = key as? String, let stringValue = value as? String {
                    dataDict[stringKey] = stringValue
                }
            }
            RealTimeDataSyncManager.shared.handleDataChange(changeType: changeType, data: dataDict)
        }

        // Show notification in foreground
        completionHandler([.banner, .sound, .badge])
    }

    // Called when user taps on notification
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo

        print("📬 Notification tapped")
        print("   User Info: \(userInfo)")

        // Handle notification tap
        // Navigate to appropriate screen based on notification data

        completionHandler()
    }

    // MARK: - Helper Methods

    private func getOrCreateDeviceId() -> String {
        let key = "device_id"
        let defaults = UserDefaults.standard

        if let existingId = defaults.string(forKey: key) {
            print("📱 Using existing device ID: \(existingId)")
            return existingId
        }

        let newId = "ios_\(UUID().uuidString)"
        defaults.set(newId, forKey: key)
        defaults.synchronize()
        print("🆕 Generated new device ID: \(newId)")
        return newId
    }

    private func getAppVersion() -> String {
        if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
            return version
        }
        return "1.0.0"
    }
}

// MARK: - FCM Token Registration Helper

/**
 * Helper class to handle FCM token registration with KMP code
 * Uses nonisolated(unsafe) to bypass Swift 6 sendability checks for KMP interop
 */
private final class FCMTokenRegistrationHelper: @unchecked Sendable {

    nonisolated(unsafe) static let shared = FCMTokenRegistrationHelper()

    private init() {}

    /// Register FCM token with backend
    /// Uses nonisolated(unsafe) to bypass strict sendability checks for KMP interop
    nonisolated func register(
        deviceId: String,
        token: String,
        deviceName: String,
        appVersion: String
    ) {
        // Run registration on main thread using DispatchQueue
        // This avoids Swift 6 actor isolation issues entirely
        DispatchQueue.main.async { [self] in
            self.performRegistration(
                deviceId: deviceId,
                token: token,
                deviceName: deviceName,
                appVersion: appVersion
            )
        }
    }

    /// Perform the actual registration using async/await in a controlled context
    private func performRegistration(
        deviceId: String,
        token: String,
        deviceName: String,
        appVersion: String
    ) {
        // Use Task.init which inherits actor context from the synchronous calling context
        Task {
            do {
                let fcmManager = FcmManager.Companion.shared.instance
                // Since we're @unchecked Sendable, the compiler doesn't check the return type
                _ = try await fcmManager.registerToken(
                    deviceId: deviceId,
                    token: token,
                    deviceName: deviceName,
                    platform: "ios",
                    appVersion: appVersion
                )
                print("✅ Token registered successfully")
            } catch {
                print("❌ Error during token registration: \(error.localizedDescription)")
            }
        }
    }
}
