import SwiftUI
import UIKit
import FirebaseCore
import FirebaseMessaging
import FirebaseCrashlytics
import UserNotifications
@preconcurrency import shared

@main
struct iOSApp: App {

	init() {
		// Configure Firebase (includes Crashlytics for crash reporting)
		FirebaseApp.configure()
		print("✅ Firebase configured")

		// Initialize NetworkConstants with PreferencesManager for server configuration
		let preferencesManager = PreferencesManager()
		NetworkConstants.shared.initialize(prefs: preferencesManager)
		print("✅ NetworkConstants initialized with PreferencesManager")

		// Firebase Analytics is now ready
		// Analytics events will be tracked via IOSAnalyticsBridge
		print("✅ Firebase Analytics ready")

		// Setup Firebase Messaging
		IOSFirebaseMessagingDelegate.shared.setup()
		print("✅ Firebase Messaging setup complete")

		// Start Network Monitor
		_ = IOSNetworkMonitor.shared
		print("✅ Network Monitor started")

		// Register and schedule background sync
		IOSBackgroundSyncHandler.register()
		IOSBackgroundSyncHandler.schedule()
		print("✅ Background sync registered and scheduled")

		// Initialize notification permission callbacks
		// Use Task to ensure KMP code runs on proper MainActor context
		let callbacksImpl = NotificationPermissionCallbacksImpl()
		Task { @MainActor in
			IOSNotificationPermissionCallbacks.shared.initialize(callbacks: callbacksImpl)
			print("✅ Notification permission callbacks initialized")
		}

		// Log app open event via Kotlin Analytics Tracker
		// Use Task to ensure KMP code runs on proper MainActor context
		Task { @MainActor in
			AnalyticsTracker.shared.logAppOpen()
			print("✅ App open event logged")
		}
	}

	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}

/**
 * Implementation of notification permission callbacks
 */
class NotificationPermissionCallbacksImpl: NSObject, IOSNotificationPermissionCallbacksProtocol {
    func hasPermission() -> Bool {
        // Synchronous check using semaphore
        var authorized = false
        let semaphore = DispatchSemaphore(value: 0)

        UNUserNotificationCenter.current().getNotificationSettings { settings in
            authorized = settings.authorizationStatus == .authorized
            semaphore.signal()
        }

        _ = semaphore.wait(timeout: .now() + 0.5)
        return authorized
    }

    func requestPermission() {
        let center = UNUserNotificationCenter.current()

        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .notDetermined:
                center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
                    DispatchQueue.main.async {
                        if let error = error {
                            print("❌ Notification permission error: \(error.localizedDescription)")
                            NotificationPermissionBridge.shared.onPermissionResult(granted: false)
                        } else {
                            print(granted ? "✅ Notification permission granted" : "⛔️ Notification permission denied")
                            NotificationPermissionBridge.shared.onPermissionResult(granted: granted)
                        }
                    }
                }
            case .denied:
                DispatchQueue.main.async {
                    print("⚠️ Notifications previously denied - notifying app")
                    NotificationPermissionBridge.shared.onPermissionResult(granted: false)
                }
            case .authorized, .provisional, .ephemeral:
                DispatchQueue.main.async {
                    print("✅ Notifications already authorized")
                    NotificationPermissionBridge.shared.onPermissionResult(granted: true)
                }
            @unknown default:
                DispatchQueue.main.async {
                    NotificationPermissionBridge.shared.onPermissionResult(granted: false)
                }
            }
        }
    }

    func openAppSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            return
        }

        // Use Task + MainActor.run to satisfy Swift 6 concurrency requirements
        // UIApplication.shared is MainActor-isolated
        Task { @MainActor in
            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl, options: [:], completionHandler: nil)
            }
        }
    }
}
