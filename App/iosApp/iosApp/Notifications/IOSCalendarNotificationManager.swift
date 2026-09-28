import Foundation
@preconcurrency import UserNotifications
import shared

/**
 * iOS notification manager for calendar event notifications
 * Uses UNUserNotificationCenter for scheduling and displaying notifications
 */
@MainActor
@objc
class IOSCalendarNotificationManager: NSObject {

    static let shared = IOSCalendarNotificationManager()

    private let notificationCenter = UNUserNotificationCenter.current()
    private let categoryIdentifier = "CALENDAR_EVENT"

    private override init() {
        super.init()
        setupNotificationCategories()
    }

    /// Request notification permission from user.
    /// Must be nonisolated to avoid MainActor assertion when iOS calls completion on background queue.
    nonisolated func requestPermission(completion: @escaping @Sendable (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            // Hop to main queue before calling completion
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ Notification permission error: \(error.localizedDescription)")
                    completion(false)
                } else {
                    print(granted ? "✅ Notification permission granted" : "⚠️ Notification permission denied")
                    completion(granted)
                }
            }
        }
    }

    /// Check if notification permission is granted.
    /// Must be nonisolated to avoid MainActor assertion when iOS calls completion on background queue.
    nonisolated func hasPermission(completion: @escaping @Sendable (Bool) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let isAuthorized = settings.authorizationStatus == .authorized
            DispatchQueue.main.async {
                completion(isAuthorized)
            }
        }
    }

    /**
     * Setup notification categories and actions
     */
    private func setupNotificationCategories() {
        let viewAction = UNNotificationAction(
            identifier: "VIEW_EVENT",
            title: "View Event",
            options: [.foreground]
        )

        let category = UNNotificationCategory(
            identifier: categoryIdentifier,
            actions: [viewAction],
            intentIdentifiers: [],
            options: []
        )

        notificationCenter.setNotificationCategories([category])
    }

    /**
     * Schedule all notifications for a calendar event
     */
    func scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager
    ) {
        let settings = preferencesManager.getNotificationSettings()

        // Calculate notification times using shared algorithm
        // Use fromEpochMilliseconds to create current time Instant
        let currentTimeMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let currentTime = Kotlinx_datetimeInstant.Companion.shared.fromEpochMilliseconds(epochMilliseconds: currentTimeMillis)

        let notificationTimes = NotificationScheduler.shared.calculateNotificationTimes(
            event: event,
            settings: settings,
            currentTime: currentTime
        )

        // Schedule each notification
        for notificationTime in notificationTimes {
            scheduleNotification(
                event: event,
                notificationTime: notificationTime
            )
        }
    }

    /**
     * Schedule a single notification using UNCalendarNotificationTrigger
     */
    private func scheduleNotification(
        event: CalendarEvent,
        notificationTime: NotificationTime
    ) {
        guard let eventId = event.id else {
            return
        }

        // Parse notification time
        guard let notificationDate = parseInstantToDate(notificationTime.dateTime) else {
            return
        }

        // Check if notification time is in the future
        if notificationDate <= Date() {
            return
        }

        // Format notification message
        let message = NotificationScheduler.shared.formatNotificationMessage(
            event: event,
            notificationTime: notificationTime
        )

        // Create notification content
        let content = UNMutableNotificationContent()
        content.title = message.title
        content.body = message.body
        content.sound = .default
        content.categoryIdentifier = categoryIdentifier
        content.userInfo = [
            "eventId": eventId,
            "notificationTime": notificationTime.dateTime
        ]

        // Create calendar trigger
        let triggerDate = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute, .second],
            from: notificationDate
        )
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerDate, repeats: false)

        // Create notification request with unique identifier
        let identifier = "\(eventId)-\(notificationTime.dateTime)"
        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            trigger: trigger
        )

        // Schedule notification
        notificationCenter.add(request) { error in
            if let error = error {
                print("❌ Failed to schedule notification: \(error.localizedDescription)")
            }
        }
    }

    /// Cancel all notifications for a specific event.
    /// Must be nonisolated to avoid MainActor assertion when iOS calls completion on background queue.
    nonisolated func cancelNotifications(eventId: String) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let identifiersToRemove = requests
                .filter { $0.identifier.hasPrefix(eventId) }
                .map { $0.identifier }

            center.removePendingNotificationRequests(withIdentifiers: identifiersToRemove)
        }
    }

    /**
     * Cancel all calendar notifications
     */
    func cancelAllNotifications() {
        notificationCenter.removeAllPendingNotificationRequests()
    }

    /// Get count of pending notifications.
    /// Must be nonisolated to avoid MainActor assertion when iOS calls completion on background queue.
    nonisolated func getPendingNotificationCount(completion: @escaping @Sendable (Int) -> Void) {
        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            let count = requests.count
            DispatchQueue.main.async {
                completion(count)
            }
        }
    }

    /**
     * Parse ISO 8601 instant string to Date
     */
    private func parseInstantToDate(_ instantString: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        if let date = formatter.date(from: instantString) {
            return date
        }

        // Try without fractional seconds
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: instantString)
    }
}

/// Extension to make IOSCalendarNotificationManager accessible from Kotlin.
extension IOSCalendarNotificationManager {

    @objc static func requestNotificationPermission(completion: @escaping @Sendable (Bool) -> Void) {
        shared.requestPermission(completion: completion)
    }

    @objc static func hasNotificationPermission(completion: @escaping @Sendable (Bool) -> Void) {
        shared.hasPermission(completion: completion)
    }

    @objc static func scheduleEventNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager
    ) {
        shared.scheduleNotifications(
            event: event,
            preferencesManager: preferencesManager
        )
    }

    @objc static func cancelEventNotifications(eventId: String) {
        shared.cancelNotifications(eventId: eventId)
    }

    @objc static func cancelAll() {
        shared.cancelAllNotifications()
    }
}
