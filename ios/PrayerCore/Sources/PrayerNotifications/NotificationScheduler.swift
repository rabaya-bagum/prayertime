import Foundation
import PrayerCore
import UserNotifications

/// The parts of UNUserNotificationCenter the scheduler uses, so tests can substitute a fake.
public protocol NotificationCenterClient: AnyObject {
    func pendingIdentifiers() async -> Set<String>
    func add(_ request: UNNotificationRequest) async throws
    func removePending(_ identifiers: [String])
}

extension UNUserNotificationCenter: NotificationCenterClient {
    public func pendingIdentifiers() async -> Set<String> {
        Set(await pendingNotificationRequests().map(\.identifier))
    }

    public func removePending(_ identifiers: [String]) {
        removePendingNotificationRequests(withIdentifiers: identifiers)
    }
}

/// Keeps the 64 pending notifications equal to `NotificationWindowPlanner.plan`.
///
/// Call `refresh` on every launch, from the BGAppRefreshTask handler, and on
/// `UIApplication.significantTimeChangeNotification` and
/// `NSNotification.Name.NSSystemTimeZoneDidChange`. Only changed requests are
/// touched, so calling it often is cheap. Main-actor isolated so a Swift 6 app
/// can call it from its view model without sending non-Sendable state.
@MainActor
public struct NotificationScheduler {
    private let center: NotificationCenterClient
    private let text: NotificationText

    public init(center: NotificationCenterClient = UNUserNotificationCenter.current(), text: NotificationText = .english) {
        self.center = center
        self.text = text
    }

    /// Pass nil before onboarding (or when everything is off) to clear all prayer notifications.
    @discardableResult
    public func refresh(_ inputs: ScheduleInputs?, now: Date = Date()) async throws -> NotificationWindowPlanner.Diff {
        let desired = inputs.map { NotificationWindowPlanner.plan($0, now: now) } ?? []
        let pending = await center.pendingIdentifiers().filter(Self.isPrayerAlert)
        let diff = NotificationWindowPlanner.diff(pending: pending, desired: desired)
        if !diff.remove.isEmpty {
            center.removePending(Array(diff.remove))
        }
        for alert in diff.add {
            try await center.add(request(for: alert, timeZone: inputs?.place.timeZone ?? .current))
        }
        return diff
    }

    func request(for alert: PlannedAlert, timeZone: TimeZone) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.threadIdentifier = "prayer"
        content.userInfo = [
            UserInfoKey.kind: alert.kind.rawValue,
            UserInfoKey.prayer: alert.prayer?.rawValue ?? "",
            UserInfoKey.sound: alert.sound ?? "",
        ]
        let prayerName = alert.prayer.map(text.prayerName) ?? ""
        let clock = alert.prayerTime.map { text.time($0, timeZone) } ?? ""
        switch alert.kind {
        case .adhan:
            content.title = prayerName
            content.body = text.timeFor(prayerName, alert.prayer)
            // iOS plays only bundled aiff/wav/caf files under 30 s; anything else falls back to the default sound.
            content.sound = UNNotificationSound(named: UNNotificationSoundName(Self.clipFileName(alert.sound ?? PrayerAlert.defaultSound)))
            content.interruptionLevel = .timeSensitive
        case .silent:
            content.title = prayerName
            content.body = text.timeFor(prayerName, alert.prayer)
            content.sound = nil
            content.interruptionLevel = .passive
        case .reminder:
            content.title = prayerName
            content.body = text.reminder(prayerName, clock)
            content.sound = .default
        case .keepAlive:
            content.title = text.keepAliveTitle
            content.body = text.keepAliveBody(alert.prayerTime.map { text.day($0, timeZone) } ?? "")
            content.sound = .default
        }
        return UNNotificationRequest(identifier: alert.identifier, content: content, trigger: Self.trigger(at: alert.time))
    }

    /// An absolute trigger: components in UTC with the zone attached, so device
    /// time-zone changes and DST can't shift it.
    nonisolated static func trigger(at date: Date) -> UNCalendarNotificationTrigger {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        var components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        return UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
    }

    /// Bundle a `<sound>_clip.caf` per reciter: the takbir section, under 30 seconds.
    public nonisolated static func clipFileName(_ sound: String) -> String { "\(sound)_clip.caf" }

    nonisolated static func isPrayerAlert(_ identifier: String) -> Bool {
        let kind = identifier.split(separator: "|", maxSplits: 1).first.map(String.init) ?? ""
        return identifier.contains("|") && AlertKind(rawValue: kind) != nil
    }

    /// Keys in `UNNotificationContent.userInfo`. On tap, the app reads these and
    /// plays the full adhan in the foreground.
    public enum UserInfoKey {
        public static let kind = "kind"
        public static let prayer = "prayer"
        public static let sound = "sound"
    }
}

/// User-facing strings. Swap in localized versions from the app's string catalog.
public struct NotificationText {
    public var prayerName: (Prayer) -> String
    public var timeFor: (_ prayerName: String, _ prayer: Prayer?) -> String
    public var reminder: (_ prayerName: String, _ time: String) -> String
    public var keepAliveTitle: String
    public var keepAliveBody: (_ lastDay: String) -> String
    public var time: (Date, TimeZone) -> String
    public var day: (Date, TimeZone) -> String

    public init(
        prayerName: @escaping (Prayer) -> String,
        timeFor: @escaping (String, Prayer?) -> String,
        reminder: @escaping (String, String) -> String,
        keepAliveTitle: String,
        keepAliveBody: @escaping (String) -> String,
        time: @escaping (Date, TimeZone) -> String,
        day: @escaping (Date, TimeZone) -> String
    ) {
        self.prayerName = prayerName
        self.timeFor = timeFor
        self.reminder = reminder
        self.keepAliveTitle = keepAliveTitle
        self.keepAliveBody = keepAliveBody
        self.time = time
        self.day = day
    }

    public static let english = NotificationText(
        prayerName: { prayer in
            switch prayer {
            case .fajr: return "Fajr"
            case .sunrise: return "Sunrise"
            case .dhuhr: return "Dhuhr"
            case .asr: return "Asr"
            case .maghrib: return "Maghrib"
            case .isha: return "Isha"
            }
        },
        timeFor: { name, prayer in
            prayer == .sunrise ? "Sunrise: the time for Fajr has ended" : "It's time for \(name)"
        },
        reminder: { name, time in "\(name) at \(time)" },
        keepAliveTitle: "Keep your adhans coming",
        keepAliveBody: { day in "Adhans are scheduled until \(day). Open the app to schedule the next two weeks." },
        time: { date, zone in
            let formatter = DateFormatter()
            formatter.timeStyle = .short
            formatter.dateStyle = .none
            formatter.timeZone = zone
            return formatter.string(from: date)
        },
        day: { date, zone in
            let formatter = DateFormatter()
            formatter.setLocalizedDateFormatFromTemplate("EEEMMMd")
            formatter.timeZone = zone
            return formatter.string(from: date)
        }
    )
}
