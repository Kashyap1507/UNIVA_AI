import Foundation
@preconcurrency import shared

/**
 * Adapter that implements Kotlin NotificationCallbacks interface
 * Bridges Kotlin calls to Swift IOSCalendarNotificationManager
 */
class IOSNotificationCallbacksAdapter: NotificationCallbacks {

    nonisolated func requestPermission(completion: @escaping @Sendable (KotlinBoolean) -> Void) {
        print("🔔 Swift Adapter: Request notification permission")
        Task { @MainActor in
            IOSCalendarNotificationManager.shared.requestPermission { granted in
                print("   Permission result: \(granted)")
                completion(KotlinBoolean(bool: granted))
            }
        }
    }

    nonisolated func hasPermission(completion: @escaping @Sendable (KotlinBoolean) -> Void) {
        print("🔔 Swift Adapter: Check notification permission")
        Task { @MainActor in
            IOSCalendarNotificationManager.shared.hasPermission { hasPermission in
                print("   Has permission: \(hasPermission)")
                completion(KotlinBoolean(bool: hasPermission))
            }
        }
    }

    nonisolated func scheduleNotifications(
        event: CalendarEvent,
        preferencesManager: PreferencesManager
    ) {
        print("🔔 Swift Adapter: Schedule notifications for event: \(event.title)")
        Task { @MainActor [event, preferencesManager] in
            IOSCalendarNotificationManager.shared.scheduleNotifications(
                event: event,
                preferencesManager: preferencesManager
            )
        }
    }

    nonisolated func cancelNotifications(eventId: String) {
        print("🔔 Swift Adapter: Cancel notifications for event: \(eventId)")
        Task { @MainActor in
            IOSCalendarNotificationManager.shared.cancelNotifications(eventId: eventId)
        }
    }

    nonisolated func cancelAllNotifications() {
        print("🔔 Swift Adapter: Cancel all notifications")
        Task { @MainActor in
            IOSCalendarNotificationManager.shared.cancelAllNotifications()
        }
    }
}
