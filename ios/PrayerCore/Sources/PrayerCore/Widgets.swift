import Foundation

/// What the app shares with its widgets: everything needed to calculate times,
/// written to the App Group's UserDefaults whenever the place or settings change.
public struct SharedConfiguration: Equatable, Codable, Sendable {
    public static let appGroup = "group.io.github.rabayabagum.prayertime"
    private static let key = "widgetConfiguration"

    public var place: Place
    /// "Ontario, Canada", shown under the place name.
    public var subtitle: String
    public var settings: PrayerSettings

    public init(place: Place, subtitle: String, settings: PrayerSettings) {
        self.place = place
        self.subtitle = subtitle
        self.settings = settings
    }

    /// Nil before the app has run once, or if the App Group isn't available.
    public static func load(from defaults: UserDefaults? = UserDefaults(suiteName: appGroup)) -> SharedConfiguration? {
        defaults?.data(forKey: key).flatMap { try? JSONDecoder().decode(SharedConfiguration.self, from: $0) }
    }

    public func save(to defaults: UserDefaults? = UserDefaults(suiteName: SharedConfiguration.appGroup)) {
        defaults?.set(try? JSONEncoder().encode(self), forKey: Self.key)
    }
}

/// One widget timeline entry: what the widget shows from `date` until the next entry.
public struct PrayerWidgetEntry: Equatable, Sendable {
    public let date: Date
    /// The civil day `date` falls on at the place, with its six times.
    public let day: CivilDate
    public let times: [Prayer: Date]
    public let hijri: HijriDate
    /// The next prayer (or sunrise) strictly after `date`.
    public let next: PrayerEvent
}

/// Widget timelines. Each entry starts at a prayer time or a local midnight, so
/// the widget switches to the next prayer the moment one begins, and countdowns
/// run on their own (`Text(timerInterval:)`) without spending refresh budget.
public enum WidgetTimeline {
    /// Entries from `now` covering the next `hours`; reload after the last one.
    public static func entries(_ configuration: SharedConfiguration, from now: Date, hours: Int = 36) -> [PrayerWidgetEntry] {
        let place = configuration.place
        let settings = configuration.settings
        let end = now.addingTimeInterval(TimeInterval(hours * 3600))

        // Boundaries: every prayer time and every local midnight in the window.
        var boundaries: [Date] = [now]
        var date = CivilDate(now, in: place.timeZone)
        while date.at(hour: 0, in: place.timeZone) < end {
            let schedule = PrayerCalculator.day(at: place, on: date, settings: settings)
            boundaries += Prayer.allCases.map { schedule[$0] }
            date = date.adding(days: 1)
            boundaries.append(date.at(hour: 0, in: place.timeZone))
        }
        let starts = Set(boundaries.filter { $0 >= now && $0 < end }).sorted()

        return starts.map { start in
            let day = CivilDate(start, in: place.timeZone)
            let schedule = PrayerCalculator.day(at: place, on: day, settings: settings)
            return PrayerWidgetEntry(
                date: start,
                day: day,
                times: schedule.times,
                hijri: schedule.hijri,
                next: PrayerCalculator.upcoming(at: place, settings: settings, from: start, count: 1)[0]
            )
        }
    }
}
