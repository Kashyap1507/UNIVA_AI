import Foundation
import EventKit

/// iOS Calendar Manager using EventKit.
/// Provides access to device calendars (iCloud, Google Calendar, etc.).
@MainActor
@objc public class IOSCalendarManager: NSObject {

    private let eventStore = EKEventStore()

    // MARK: - Permission Management

    /// Request calendar access permission.
    /// Must be nonisolated - iOS calls completion on background queue.
    @objc nonisolated public func requestCalendarAccess(completion: @escaping @Sendable (Bool) -> Void) {
        let store = EKEventStore()
        if #available(iOS 17.0, *) {
            store.requestFullAccessToEvents { granted, _ in
                DispatchQueue.main.async {
                    completion(granted)
                }
            }
        } else {
            store.requestAccess(to: .event) { granted, _ in
                DispatchQueue.main.async {
                    completion(granted)
                }
            }
        }
    }

    /// Check if calendar permission is granted.
    @objc nonisolated public func hasCalendarPermission() -> Bool {
        if #available(iOS 17.0, *) {
            return EKEventStore.authorizationStatus(for: .event) == .fullAccess
        } else {
            return EKEventStore.authorizationStatus(for: .event) == .authorized
        }
    }

    /// Check if calendar permission is granted (async version for callbacks).
    @objc nonisolated public func checkCalendarAccess(completion: @escaping @Sendable (Bool) -> Void) {
        let hasPermission = self.hasCalendarPermission()
        DispatchQueue.main.async {
            completion(hasPermission)
        }
    }

    // MARK: - Get Device Calendars

    /// Get list of device calendars filtered by type
    /// - Parameters:
    ///   - type: Calendar type filter ("google", "apple", or "all")
    ///   - completion: Completion handler with array of calendar dictionaries
    @objc public func getDeviceCalendars(_ type: String, completion: @escaping ([Any]?) -> Void) {
        guard hasCalendarPermission() else {
            completion(nil)
            return
        }

        let calendars = eventStore.calendars(for: .event)
        var filteredCalendars: [EKCalendar] = []

        for calendar in calendars {
            let sourceType = calendar.source.sourceType
            let sourceTitle = calendar.source.title.lowercased()

            let shouldInclude: Bool
            switch type.lowercased() {
            case "google":
                // Google calendars are typically CalDAV with "Google" in source title
                shouldInclude = (sourceType == .calDAV && sourceTitle.contains("google")) ||
                                sourceTitle.contains("gmail")

            case "apple":
                // Apple calendars are local or CalDAV (including iCloud)
                shouldInclude = sourceType == .local || (sourceType == .calDAV && !sourceTitle.contains("google"))

            default: // "all"
                shouldInclude = true
            }

            if shouldInclude {
                filteredCalendars.append(calendar)
            }
        }

        // Convert to array of dictionaries
        let calendarDicts = filteredCalendars.map { calendar -> [String: Any] in
            return [
                "id": calendar.calendarIdentifier,
                "name": calendar.title,
                "accountName": calendar.source.title,
                "accountType": sourceTypeString(calendar.source.sourceType),
                "color": rgbColor(from: calendar.cgColor),
                "isPrimary": calendar.source.sourceType == .local
            ]
        }

        completion(calendarDicts)
    }

    // MARK: - Get Events

    /// Get calendar events from selected calendars within date range
    /// - Parameters:
    ///   - calendarIds: Array of calendar IDs to fetch from
    ///   - startDate: Start date string (YYYY-MM-DD)
    ///   - endDate: End date string (YYYY-MM-DD)
    ///   - filterStudyRelated: If true, only return study-related events
    ///   - completion: Completion handler with array of event dictionaries
    @objc public func getEvents(
        calendarIds: [String],
        startDate: String,
        endDate: String,
        filterStudyRelated: Bool,
        completion: @escaping ([Any]?) -> Void
    ) {
        guard hasCalendarPermission() else {
            completion(nil)
            return
        }

        // Parse dates
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"

        guard let start = dateFormatter.date(from: startDate),
              let end = dateFormatter.date(from: endDate) else {
            completion(nil)
            return
        }

        // Get calendars by IDs
        let allCalendars = eventStore.calendars(for: .event)
        let selectedCalendars = allCalendars.filter { calendarIds.contains($0.calendarIdentifier) }

        guard !selectedCalendars.isEmpty else {
            completion([])
            return
        }

        // Create predicate for date range
        let predicate = eventStore.predicateForEvents(
            withStart: start,
            end: Calendar.current.date(byAdding: .day, value: 1, to: end) ?? end,
            calendars: selectedCalendars
        )

        // Fetch events
        let events = eventStore.events(matching: predicate)

        // Filter and convert events
        var eventDicts: [[String: Any]] = []

        for event in events {
            // Skip events marked as deleted
            if event.calendar == nil { continue }

            // Apply study-related filter
            if filterStudyRelated && !isStudyRelated(event: event) {
                continue
            }

            // Convert to dictionary
            let eventDict = convertEventToDict(event)
            eventDicts.append(eventDict)
        }

        completion(eventDicts)
    }

    // MARK: - Helper Methods

    private func sourceTypeString(_ sourceType: EKSourceType) -> String {
        switch sourceType {
        case .local:
            return "Local"
        case .exchange:
            return "Exchange"
        case .calDAV:
            return "CalDAV"
        case .mobileMe:
            return "MobileMe"
        case .subscribed:
            return "Subscribed"
        case .birthdays:
            return "Birthdays"
        @unknown default:
            return "Unknown"
        }
    }

    private func rgbColor(from cgColor: CGColor) -> Int {
        guard let components = cgColor.components, components.count >= 3 else {
            return 0x3B82F6 // Default blue
        }

        let r = Int(components[0] * 255)
        let g = Int(components[1] * 255)
        let b = Int(components[2] * 255)

        return (r << 16) | (g << 8) | b
    }

    private func isStudyRelated(event: EKEvent) -> Bool {
        let title = event.title ?? ""
        let notes = event.notes ?? ""
        let combinedText = "\(title) \(notes)".lowercased()

        // Same list as StudyEventKeywords in shared/calendar/CalendarProvider.kt
        let studyKeywords = [
            "test", "exam", "quiz", "assessment", "midterm", "final",
            "homework", "assignment", "problem set", "project", "essay", "report", "paper",
            "presentation", "lab", "practicum", "thesis", "dissertation", "capstone",
            "due", "deadline", "submit", "submission", "turn in",
            "class", "lecture", "tutorial", "seminar", "workshop", "recitation",
            "office hours", "study", "review", "practice", "course",
            "study group", "supervisor", "advisor", "defense", "viva", "colloquium"
        ]

        return studyKeywords.contains { combinedText.contains($0) }
    }

    private func convertEventToDict(_ event: EKEvent) -> [String: Any] {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm"

        let isoFormatter = ISO8601DateFormatter()

        var dict: [String: Any] = [
            "title": event.title ?? "Untitled Event",
            "date": dateFormatter.string(from: event.startDate),
            // No "type": the shared code infers it from the title (EventType.infer), like on Android
            "confidence": 0.85,
            // Device calendar sync fields for deduplication
            "deviceEventId": event.eventIdentifier ?? "",
            "calendarId": event.calendar.calendarIdentifier,
            "lastSyncedAt": isoFormatter.string(from: Date())
        ]

        // Add time if not all-day event
        if !event.isAllDay {
            dict["time"] = timeFormatter.string(from: event.startDate)
        }

        // Add description if available
        if let notes = event.notes, !notes.isEmpty {
            dict["description"] = notes
        }

        // Add duration
        if let endDate = event.endDate {
            let duration = Int(endDate.timeIntervalSince(event.startDate) / 60)
            dict["durationMinutes"] = duration
        }

        return dict
    }
}
