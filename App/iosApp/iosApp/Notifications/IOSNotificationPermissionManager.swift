//
//  IOSNotificationPermissionManager.swift
//  iosApp
//
//  Manages notification permissions for iOS
//

import Foundation
import UIKit
import UserNotifications
import shared

/**
 * iOS Notification Permission Manager
 * Handles checking and requesting notification permissions
 */
class IOSNotificationPermissionManager {

    static let shared = IOSNotificationPermissionManager()

    private init() {}

    /**
     * Check if notification permission is granted
     */
    func hasPermission(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                completion(settings.authorizationStatus == .authorized)
            }
        }
    }

    /**
     * Synchronous check - returns cached status
     * Note: May not be 100% accurate without async check
     */
    func hasPermissionSync() -> Bool {
        // This is a best-effort synchronous check
        // For accurate results, use the async version
        var authorized = false
        let semaphore = DispatchSemaphore(value: 0)

        UNUserNotificationCenter.current().getNotificationSettings { settings in
            authorized = settings.authorizationStatus == .authorized
            semaphore.signal()
        }

        // Wait up to 0.5 seconds
        _ = semaphore.wait(timeout: .now() + 0.5)
        return authorized
    }

    /**
     * Request notification permission
     */
    func requestPermission(completion: @escaping (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()

        // Check current status first
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .notDetermined:
                // First time - request permission
                center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
                    DispatchQueue.main.async {
                        if let error = error {
                            print("❌ Notification permission error: \(error.localizedDescription)")
                            completion(false)
                        } else {
                            print(granted ? "✅ Notification permission granted" : "⛔️ Notification permission denied")
                            // Call back to Kotlin
                            NotificationPermissionBridge.companion.onPermissionResult(granted: granted)
                            completion(granted)
                        }
                    }
                }

            case .denied:
                // Previously denied - direct to settings
                DispatchQueue.main.async {
                    print("⚠️ Notifications denied - directing to settings")
                    self.openAppSettings()
                    completion(false)
                }

            case .authorized, .provisional, .ephemeral:
                // Already authorized
                DispatchQueue.main.async {
                    completion(true)
                }

            @unknown default:
                DispatchQueue.main.async {
                    completion(false)
                }
            }
        }
    }

    /**
     * Open app settings
     */
    func openAppSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            return
        }

        if UIApplication.shared.canOpenURL(settingsUrl) {
            UIApplication.shared.open(settingsUrl, options: [:], completionHandler: nil)
        }
    }
}

