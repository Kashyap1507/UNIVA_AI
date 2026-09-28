import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics

/**
 * iOS Analytics Bridge for Kotlin Multiplatform
 *
 * This bridge provides Firebase Analytics functionality to Kotlin code
 * running on iOS via the shared framework.
 *
 * Usage from Kotlin:
 * ```kotlin
 * IOSAnalyticsBridge.shared.logEvent(
 *     eventName = "work_scan_uploaded",
 *     parameters = mapOf("course_id" to "123")
 * )
 * ```
 */
@objc public class IOSAnalyticsBridge: NSObject {

    // Singleton instance for access from Kotlin
    @objc public static let shared = IOSAnalyticsBridge()

    // Track timed events
    private var timedEvents: [String: TimeInterval] = [:]

    private override init() {
        super.init()
    }

    // MARK: - Event Logging

    /**
     * Log a custom analytics event
     *
     * @param eventName The name of the event (e.g., "work_scan_uploaded")
     * @param parameters Optional dictionary of parameters
     */
    @objc public func logEvent(eventName: String, parameters: [String: Any]?) {
        // Convert parameters to Firebase-compatible format
        var analyticsParams: [String: Any]? = nil

        if let params = parameters {
            analyticsParams = convertParameters(params)
        }

        Analytics.logEvent(eventName, parameters: analyticsParams)
        print("📊 Analytics Event: \(eventName) - Params: \(parameters ?? [:])")
    }

    /**
     * Log a screen view event
     *
     * @param screenName The name of the screen (e.g., "home_screen")
     * @param screenClass Optional class name of the screen
     */
    @objc public func logScreenView(screenName: String, screenClass: String?) {
        var params: [String: Any] = [
            AnalyticsParameterScreenName: screenName
        ]

        if let screenClass = screenClass {
            params[AnalyticsParameterScreenClass] = screenClass
        }

        Analytics.logEvent(AnalyticsEventScreenView, parameters: params)
        print("📱 Screen View: \(screenName)\(screenClass != nil ? " (\(screenClass!))" : "")")
    }

    // MARK: - User Properties

    /**
     * Set the user ID for analytics
     *
     * @param userId The unique user ID (or nil to clear)
     */
    @objc public func setUserId(_ userId: String?) {
        Analytics.setUserID(userId)

        // Also set for Crashlytics
        if let userId = userId {
            Crashlytics.crashlytics().setUserID(userId)
            print("👤 User ID Set: \(userId)")
        } else {
            print("👤 User ID Cleared")
        }
    }

    /**
     * Set a user property
     *
     * @param name The property name
     * @param value The property value
     */
    @objc public func setUserProperty(name: String, value: String?) {
        Analytics.setUserProperty(value, forName: name)
        print("🏷️ User Property: \(name) = \(value ?? "nil")")
    }

    // MARK: - Analytics Control

    /**
     * Enable or disable analytics collection
     *
     * @param enabled Whether analytics collection is enabled
     */
    @objc public func setAnalyticsEnabled(_ enabled: Bool) {
        Analytics.setAnalyticsCollectionEnabled(enabled)
        print("🔒 Analytics \(enabled ? "Enabled" : "Disabled")")
    }

    /**
     * Log app open event
     */
    @objc public func logAppOpen() {
        Analytics.logEvent(AnalyticsEventAppOpen, parameters: nil)
        print("🚀 App Open Logged")
    }

    // MARK: - Timed Events

    /**
     * Start timing an event
     *
     * @param eventName The name of the event to time
     */
    @objc public func logTimedEventStart(eventName: String) {
        timedEvents[eventName] = Date().timeIntervalSince1970
        print("⏱️ Timed Event Started: \(eventName)")
    }

    /**
     * End timing an event and log it with duration
     *
     * @param eventName The name of the event (must match start event)
     * @param parameters Optional dictionary of parameters
     */
    @objc public func logTimedEventEnd(eventName: String, parameters: [String: Any]?) {
        guard let startTime = timedEvents.removeValue(forKey: eventName) else {
            print("⚠️ No start time found for timed event: \(eventName)")
            return
        }

        let duration = Int((Date().timeIntervalSince1970 - startTime) * 1000) // Convert to milliseconds

        var params = parameters ?? [:]
        params["duration_ms"] = duration

        logEvent(eventName: eventName, parameters: params)
        print("⏱️ Timed Event Ended: \(eventName) (\(duration)ms)")
    }

    // MARK: - Helper Methods

    /**
     * Log API call performance
     *
     * @param endpoint API endpoint path
     * @param method HTTP method
     * @param durationMs Request duration in milliseconds
     * @param statusCode HTTP status code
     * @param success Whether the request was successful
     */
    @objc public func logApiCall(
        endpoint: String,
        method: String,
        durationMs: Int,
        statusCode: Int,
        success: Bool
    ) {
        logEvent(eventName: "api_call", parameters: [
            "api_endpoint": endpoint,
            "api_method": method,
            "api_duration_ms": durationMs,
            "api_status_code": statusCode,
            "success": success
        ])
    }

    /**
     * Log sync performance
     *
     * @param syncType Type of sync (bootstrap, smart, document)
     * @param itemsSynced Number of items synced
     * @param durationMs Sync duration in milliseconds
     * @param success Whether sync was successful
     */
    @objc public func logSync(
        syncType: String,
        itemsSynced: Int,
        durationMs: Int,
        success: Bool
    ) {
        let eventName = success ? "bootstrap_sync_success" : "bootstrap_sync_failed"

        logEvent(eventName: eventName, parameters: [
            "sync_type": syncType,
            "items_synced": itemsSynced,
            "duration_ms": durationMs,
            "success": success
        ])
    }

    // MARK: - Private Methods

    /**
     * Convert parameters to Firebase-compatible types
     *
     * Firebase Analytics accepts: String, Int, Double, Bool
     * This converts any incompatible types to String
     */
    private func convertParameters(_ params: [String: Any]) -> [String: Any] {
        var converted: [String: Any] = [:]

        for (key, value) in params {
            switch value {
            case let stringValue as String:
                converted[key] = stringValue
            case let intValue as Int:
                converted[key] = intValue
            case let doubleValue as Double:
                converted[key] = doubleValue
            case let boolValue as Bool:
                converted[key] = boolValue ? 1 : 0
            case let int64Value as Int64:
                converted[key] = Int(int64Value)
            default:
                // Convert other types to String
                converted[key] = String(describing: value)
            }
        }

        return converted
    }
}

// MARK: - Singleton Access for Objective-C/Swift

/**
 * Extension to provide convenient access to analytics functions
 */
extension IOSAnalyticsBridge {

    /**
     * Track a screen view from SwiftUI
     */
    @objc public static func trackScreenView(_ screenName: String) {
        shared.logScreenView(screenName: screenName, screenClass: nil)
    }

    /**
     * Track an event from SwiftUI
     */
    @objc public static func trackEvent(_ eventName: String, parameters: [String: Any]? = nil) {
        shared.logEvent(eventName: eventName, parameters: parameters)
    }
}
