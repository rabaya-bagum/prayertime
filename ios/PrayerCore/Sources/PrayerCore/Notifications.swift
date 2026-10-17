import Foundation

/// What happens at a prayer's start time.
public enum AlertMode: String, CaseIterable, Codable, Sendable {
    /// Adhan audio: a short clip on iOS, the full adhan via a foreground service on Android.
    case adhan
    /// A notification with no sound.
    case silent
    case off
}

public struct PrayerAlert: Equatable, Codable, Sendable {
    public static let defaultSound = "makkah"

    public var mode: AlertMode
    /// Reciter/sound id. The apps map it to bundled audio files.
    public var sound: String
    /// A separate heads-up this many minutes before the prayer, or nil for none.
    public var reminderMinutesBefore: Int?

    public init(mode: AlertMode, sound: String = PrayerAlert.defaultSound, reminderMinutesBefore: Int? = nil) {
        precondition(reminderMinutesBefore.map { (1...120).contains($0) } ?? true, "Reminder must be 1–120 minutes")
        precondition(sound.allSatisfy { $0.isLowercase || $0.isNumber || $0 == "_" } && !sound.isEmpty,
                     "Sound ids are lowercase resource names: \(sound)")
        self.mode = mode
        self.sound = sound
        self.reminderMinutesBefore = reminderMinutesBefore
    }
}

public struct NotificationPreferences: Equatable, Codable, Sendable {
    /// Adhan for the five prayers. Sunrise only ends Fajr, so it is off unless the user opts in.
    public static let defaultAlerts: [Prayer: PrayerAlert] = Dictionary(uniqueKeysWithValues: Prayer.allCases.map {
        ($0, PrayerAlert(mode: $0 == .sunrise ? .off : .adhan))
    })

    public var alerts: [Prayer: PrayerAlert]

    public init(alerts: [Prayer: PrayerAlert] = NotificationPreferences.defaultAlerts) {
        self.alerts = alerts
    }

    public func alert(for prayer: Prayer) -> PrayerAlert {
        alerts[prayer] ?? NotificationPreferences.defaultAlerts[prayer]!
    }
}

/// Everything a scheduler needs, as stored by the app.
public struct ScheduleInputs: Equatable, Codable, Sendable {
    public var place: Place
    public var settings: PrayerSettings
    public var notifications: NotificationPreferences

    public init(place: Place, settings: PrayerSettings, notifications: NotificationPreferences = NotificationPreferences()) {
        self.place = place
        self.settings = settings
        self.notifications = notifications
    }
}

public enum AlertKind: String, Sendable {
    case adhan, silent, reminder, keepAlive = "keep_alive"
}

/// One notification to schedule. `identifier` encodes everything that matters
/// (kind, prayer, date, instant, sound), so two plans can be diffed by
/// identifier alone: if a setting changes an alert, its identifier changes too.
/// Same format as android/core's PlannedAlert.
public struct PlannedAlert: Equatable, Sendable {
    public let kind: AlertKind
    public let time: Date
    public let prayer: Prayer?
    public let date: CivilDate?
    public let sound: String?
    /// When the prayer itself begins; differs from `time` for reminders. Not part of `identifier`.
    public let prayerTime: Date?

    public init(kind: AlertKind, time: Date, prayer: Prayer? = nil, date: CivilDate? = nil, sound: String? = nil, prayerTime: Date? = nil) {
        self.kind = kind
        self.time = time
        self.prayer = prayer
        self.date = date
        self.sound = sound
        self.prayerTime = prayerTime
    }

    public var identifier: String {
        [kind.rawValue, date?.description ?? "-", prayer?.rawValue ?? "-", String(Int(time.timeIntervalSince1970)), sound ?? "-"]
            .joined(separator: "|")
    }
}

/// Turns prayer times and preferences into alerts. Pure, so the platform adapters stay thin.
public enum AlertPlanner {

    /// Alerts strictly after `now` whose time falls before `until`, in time order.
    public static func alerts(_ inputs: ScheduleInputs, now: Date, until: Date) -> [PlannedAlert] {
        var alerts: [PlannedAlert] = []
        let zone = inputs.place.timeZone
        // A day early for zones far from UTC, a day late for reminders crossing midnight.
        var date = CivilDate(now, in: zone).adding(days: -1)
        let lastDate = CivilDate(until, in: zone).adding(days: 1)
        while date <= lastDate {
            let day = PrayerCalculator.day(at: inputs.place, on: date, settings: inputs.settings)
            for prayer in Prayer.allCases {
                let alert = inputs.notifications.alert(for: prayer)
                guard alert.mode != .off else { continue }
                let time = day[prayer]
                alerts.append(PlannedAlert(
                    kind: alert.mode == .adhan ? .adhan : .silent,
                    time: time,
                    prayer: prayer,
                    date: date,
                    sound: alert.mode == .adhan ? alert.sound : nil,
                    prayerTime: time
                ))
                if let minutes = alert.reminderMinutesBefore {
                    alerts.append(PlannedAlert(
                        kind: .reminder, time: time.addingTimeInterval(-TimeInterval(minutes * 60)),
                        prayer: prayer, date: date, prayerTime: time
                    ))
                }
            }
            date = date.adding(days: 1)
        }
        // Stable sort keeps a reminder and an adhan at the same instant in a fixed order.
        return alerts.enumerated()
            .filter { $0.element.time > now && $0.element.time < until }
            .sorted { ($0.element.time, $0.offset) < ($1.element.time, $1.offset) }
            .map(\.element)
    }
}

/// iOS keeps at most 64 pending local notifications per app. The plan fills
/// `capacity` - 1 slots in time order and spends the last on a keep-alive
/// notification a day before the window runs out ("open the app to keep adhans
/// scheduled"), which catches users who never open the app or force-quit it.
public enum NotificationWindowPlanner {
    public static let iosCapacity = 64
    public static let horizonDays = 30
    /// The keep-alive goes off this long before the last planned alert.
    public static let keepAliveLead: TimeInterval = 24 * 3600

    public static func plan(_ inputs: ScheduleInputs, now: Date, capacity: Int = iosCapacity) -> [PlannedAlert] {
        precondition(capacity >= 2)
        let until = now.addingTimeInterval(TimeInterval(horizonDays * 86_400))
        let alerts = Array(AlertPlanner.alerts(inputs, now: now, until: until).prefix(capacity - 1))
        guard let last = alerts.last else { return [] }
        let keepAliveTime = max(last.time.addingTimeInterval(-keepAliveLead), now.addingTimeInterval(3600))
        // The keep-alive carries the last alert's instant so its text can say "scheduled until …".
        let keepAlive = PlannedAlert(kind: .keepAlive, time: keepAliveTime, prayerTime: last.time)
        return (alerts + [keepAlive]).enumerated()
            .sorted { ($0.element.time, $0.offset) < ($1.element.time, $1.offset) }
            .map(\.element)
    }

    public struct Diff: Equatable, Sendable {
        public let remove: Set<String>
        public let add: [PlannedAlert]
    }

    /// What to remove from and add to the pending set so it equals `desired`.
    public static func diff(pending: Set<String>, desired: [PlannedAlert]) -> Diff {
        let desiredIds = Set(desired.map(\.identifier))
        return Diff(remove: pending.subtracting(desiredIds), add: desired.filter { !pending.contains($0.identifier) })
    }
}
