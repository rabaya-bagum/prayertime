import Foundation
import PrayerCore

enum AlertMode: String, Codable, CaseIterable, Sendable {
    case sound, silent, off

    var displayName: String {
        switch self {
        case .sound: return "Sound"
        case .silent: return "Silent"
        case .off: return "Off"
        }
    }
}

struct AlertPreferences: Equatable, Codable, Sendable {
    var modes: [Prayer: AlertMode] = [
        .fajr: .sound, .sunrise: .off, .dhuhr: .sound, .asr: .sound, .maghrib: .sound, .isha: .sound,
    ]
    /// Minutes before each enabled prayer to send a reminder; 0 turns reminders off.
    var reminderMinutes = 0

    static let reminderChoices = [0, 5, 10, 15, 20, 30, 45, 60]

    func mode(for prayer: Prayer) -> AlertMode { modes[prayer] ?? .off }
}

struct PlannedAlert: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case adhan
        case reminder(minutesBefore: Int)
        /// The last slot: tells the user to open the app before alerts run out.
        case keepAlive
    }

    let kind: Kind
    let prayer: Prayer
    let time: Date
    let playsSound: Bool
}

/// Decides which local notifications to keep pending. iOS allows at most 64, so the
/// app refills this rolling window on every launch and background refresh, and
/// spends the last slot on a reminder to open the app (SPEC revisions).
enum AlertPlanner {
    static let systemLimit = 64
    /// No point planning further ahead than the window can hold even with alerts sparse.
    static let horizonDays = 30

    static func plan(at place: Place, settings: PrayerSettings, preferences: AlertPreferences,
                     from now: Date, limit: Int = systemLimit) -> [PlannedAlert] {
        precondition(limit >= 1)
        let enabled = Prayer.allCases.filter { preferences.mode(for: $0) != .off }
        guard !enabled.isEmpty else { return [] }

        // Reminders can fall before an adhan that is itself after `now`, so look from
        // `now` and drop anything already past.
        let horizon = now.addingTimeInterval(TimeInterval(horizonDays) * 86_400)
        let events = PrayerCalculator.upcoming(at: place, settings: settings, from: now,
                                               count: Prayer.allCases.count * horizonDays)
        var alerts: [PlannedAlert] = []
        for event in events where event.time <= horizon {
            let mode = preferences.mode(for: event.prayer)
            guard mode != .off else { continue }
            let sound = mode == .sound
            if preferences.reminderMinutes > 0 && event.prayer != .sunrise {
                let time = event.time.addingTimeInterval(-TimeInterval(preferences.reminderMinutes) * 60)
                if time > now {
                    alerts.append(PlannedAlert(kind: .reminder(minutesBefore: preferences.reminderMinutes),
                                               prayer: event.prayer, time: time, playsSound: sound))
                }
            }
            alerts.append(PlannedAlert(kind: .adhan, prayer: event.prayer, time: event.time, playsSound: sound))
        }
        alerts.sort { $0.time < $1.time }
        guard !alerts.isEmpty else { return [] }

        // The keep-alive takes the place of the first alert that didn't fit, so the user
        // hears it at a prayer time they'd otherwise have missed silently.
        let kept = Array(alerts.prefix(limit - 1))
        let replaced = alerts.count >= limit ? alerts[limit - 1] : nil
        let last = kept.last ?? alerts[0]
        let keepAlive = PlannedAlert(kind: .keepAlive,
                                     prayer: replaced?.prayer ?? last.prayer,
                                     time: replaced?.time ?? last.time.addingTimeInterval(60),
                                     playsSound: true)
        return kept + [keepAlive]
    }
}
