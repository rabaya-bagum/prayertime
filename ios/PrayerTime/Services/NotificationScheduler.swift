import Foundation
import PrayerCore
import UserNotifications

/// Replaces all pending prayer notifications with a fresh plan.
struct NotificationScheduler {
    private var center: UNUserNotificationCenter { .current() }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    func apply(_ plan: [PlannedAlert], at place: Place) async {
        center.removeAllPendingNotificationRequests()
        guard await authorizationStatus() == .authorized else { return }
        for alert in plan {
            let request = UNNotificationRequest(identifier: identifier(alert),
                                                content: content(alert, place: place),
                                                trigger: trigger(alert.time, in: place.timeZone))
            try? await center.add(request)
        }
    }

    func pendingCount() async -> Int {
        await center.pendingNotificationRequests().count
    }

    private func identifier(_ alert: PlannedAlert) -> String {
        let kind: String
        switch alert.kind {
        case .adhan: kind = "adhan"
        case .reminder: kind = "reminder"
        case .keepAlive: kind = "keepalive"
        }
        return "\(kind)-\(alert.prayer.rawValue)-\(Int(alert.time.timeIntervalSince1970))"
    }

    private func content(_ alert: PlannedAlert, place: Place) -> UNNotificationContent {
        let content = UNMutableNotificationContent()
        let time = alert.time.formatted(Date.FormatStyle(date: .omitted, time: .shortened, timeZone: place.timeZone))
        let name = alert.prayer.displayName
        switch alert.kind {
        case .adhan:
            content.title = name
            content.body = alert.prayer == .sunrise
                ? "Sunrise at \(time) in \(place.name). The Fajr window has ended."
                : "It's time for \(name) (\(time)) in \(place.name)."
        case .reminder(let minutes):
            content.title = "\(name) in \(minutes) minutes"
            content.body = "\(name) is at \(time) in \(place.name)."
        case .keepAlive:
            content.title = name
            content.body = "Open Prayer Time to keep adhan alerts scheduled. They have run out after this one."
        }
        // iOS caps custom sounds at 30 s; bundled adhan clips are still to come (SPEC: audio licensing).
        content.sound = alert.playsSound ? .default : nil
        content.threadIdentifier = "prayers"
        return content
    }

    /// Calendar components pinned to the place's zone, so a device timezone change
    /// doesn't shift alerts to the wrong wall-clock time (SPEC revisions).
    private func trigger(_ time: Date, in zone: TimeZone) -> UNCalendarNotificationTrigger {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        var components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: time)
        components.calendar = calendar
        components.timeZone = zone
        return UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
    }
}
