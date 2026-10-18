import Foundation
import Observation
import PrayerCore
import PrayerNotifications
import UserNotifications
import WidgetKit

/// Where times are calculated for. The country code picks the default method.
struct SavedLocation: Equatable, Codable, Sendable {
    var place: Place
    var subtitle: String
    var countryCode: String
    var isCurrentLocation: Bool

    init(city: City) {
        place = Place(name: city.name, latitude: city.latitude, longitude: city.longitude, timeZoneId: city.timeZoneId)
        subtitle = city.subtitle
        countryCode = city.countryCode
        isCurrentLocation = false
    }

    /// A GPS fix takes the name and IANA zone of the nearest city (SPEC revisions),
    /// rounded to ~1 km since nothing finer changes the times.
    init(fixLatitude: Double, longitude: Double, nearest city: City) {
        func round2(_ x: Double) -> Double { (x * 100).rounded() / 100 }
        place = Place(name: city.name, latitude: round2(fixLatitude), longitude: round2(longitude), timeZoneId: city.timeZoneId)
        subtitle = city.subtitle
        countryCode = city.countryCode
        isCurrentLocation = true
    }
}

@MainActor
@Observable
final class AppModel {
    private(set) var location: SavedLocation
    private(set) var settings: PrayerSettings
    /// False until the user changes a calculation setting; until then the method
    /// follows the country of the chosen location.
    private(set) var settingsCustomized: Bool
    private(set) var alerts: NotificationPreferences
    private(set) var notificationStatus: UNAuthorizationStatus = .notDetermined
    private(set) var pendingAlertCount = 0

    @ObservationIgnored private let defaults: UserDefaults
    private var center: UNUserNotificationCenter { .current() }
    @ObservationIgnored private var rescheduleTask: Task<Void, Never>?

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let location = Self.load(SavedLocation.self, "location", defaults) ?? Self.startingLocation()
        self.location = location
        settingsCustomized = defaults.bool(forKey: "settingsCustomized")
        settings = Self.load(PrayerSettings.self, "settings", defaults) ?? Defaults.settings(forCountry: location.countryCode)
        alerts = Self.load(NotificationPreferences.self, Self.alertsKey, defaults) ?? NotificationPreferences()
    }

    var countryDefaults: PrayerSettings { Defaults.settings(forCountry: location.countryCode) }

    func setLocation(_ location: SavedLocation) {
        self.location = location
        if !settingsCustomized { settings = countryDefaults }
        save()
    }

    func updateSettings(_ change: (inout PrayerSettings) -> Void) {
        var copy = settings
        change(&copy)
        guard copy != settings else { return }
        settings = copy
        settingsCustomized = true
        save()
    }

    func resetSettings() {
        settings = countryDefaults
        settingsCustomized = false
        save()
    }

    func updateAlerts(_ change: (inout NotificationPreferences) -> Void) {
        change(&alerts)
        save()
    }

    /// Asks once; afterwards the user changes it in the Settings app.
    func requestNotificationsIfNeeded() async {
        if await NotificationAccess.status() == .notDetermined {
            _ = await NotificationAccess.request()
        }
        await refreshAlerts()
    }

    /// Brings the pending notification window up to date, touching only alerts
    /// that changed. Called on launch, on becoming active, on background refresh,
    /// on timezone change and after any edit.
    func refreshAlerts() async {
        shareWithWidgets()
        notificationStatus = await NotificationAccess.status()
        let inputs = ScheduleInputs(place: location.place, settings: settings, notifications: alerts)
        let allowed = [UNAuthorizationStatus.authorized, .provisional, .ephemeral].contains(notificationStatus)
        await NotificationAccess.removeLegacyAlerts()
        try? await NotificationScheduler(center: center).refresh(allowed ? inputs : nil)
        pendingAlertCount = await NotificationAccess.pendingCount()
    }

    /// Widgets read the place and settings from the App Group. Only a real change
    /// writes and reloads them, so returning to the app doesn't churn timelines.
    private func shareWithWidgets() {
        let shared = SharedConfiguration(place: location.place, subtitle: location.subtitle, settings: settings)
        guard shared != SharedConfiguration.load() else { return }
        shared.save()
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func save() {
        Self.store(location, "location", defaults)
        Self.store(settings, "settings", defaults)
        Self.store(alerts, Self.alertsKey, defaults)
        defaults.set(settingsCustomized, forKey: "settingsCustomized")
        // Coalesce rapid edits (stepper taps) into one reschedule.
        rescheduleTask?.cancel()
        rescheduleTask = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            await refreshAlerts()
        }
    }

    /// "alerts" held the app's earlier, per-app reminder format; a new key avoids decoding it.
    private static let alertsKey = "notificationPreferences"

    /// Before the user picks anything: the largest city in the device's zone, so
    /// the first screen is already roughly right without asking for location.
    private static func startingLocation() -> SavedLocation {
        let db = CityDatabase.shared
        let city = db.largest(inTimeZone: TimeZone.current.identifier)
            ?? db.cities.first { $0.name == "Makkah" }
            ?? db.cities[0]
        return SavedLocation(city: city)
    }

    private static func load<T: Decodable>(_ type: T.Type, _ key: String, _ defaults: UserDefaults) -> T? {
        defaults.data(forKey: key).flatMap { try? JSONDecoder().decode(type, from: $0) }
    }

    private static func store<T: Encodable>(_ value: T, _ key: String, _ defaults: UserDefaults) {
        defaults.set(try? JSONEncoder().encode(value), forKey: key)
    }
}

/// UNNotificationSettings and UNNotificationRequest aren't Sendable, so they're
/// read here, off the main actor, and only plain values cross back to AppModel.
private enum NotificationAccess {
    static func status() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    static func request() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func pendingCount() async -> Int {
        await UNUserNotificationCenter.current().pendingNotificationRequests().count
    }

    /// The scheduler only manages its own identifiers, so alerts left by the
    /// app's earlier scheduler would otherwise fire alongside the new ones.
    static func removeLegacyAlerts() async {
        let center = UNUserNotificationCenter.current()
        let legacy = await center.pendingNotificationRequests().map(\.identifier).filter(isLegacyAlert)
        if !legacy.isEmpty { center.removePendingNotificationRequests(withIdentifiers: legacy) }
    }
}

/// "adhan-fajr-1767225600": the earlier scheduler's "<kind>-<prayer>-<epoch seconds>" format.
func isLegacyAlert(_ identifier: String) -> Bool {
    identifier.range(of: #"^(adhan|reminder|keepalive)-[a-z]+-\d+$"#, options: .regularExpression) != nil
}
