import Foundation
import PrayerCore
import XCTest
@testable import PrayerTime

@MainActor
final class AppModelTests: XCTestCase {
    private var defaults: UserDefaults!

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "AppModelTests")!
        defaults.removePersistentDomain(forName: "AppModelTests")
    }

    private func city(_ name: String, _ code: String) -> City {
        CityDatabase.shared.search("\(name), \(code)").first!
    }

    func testMethodFollowsCountryUntilCustomized() {
        let model = AppModel(defaults: defaults)
        model.setLocation(SavedLocation(city: city("Karachi", "PK")))
        XCTAssertEqual(model.settings.method, .karachi)
        XCTAssertEqual(model.settings.madhab, .hanafi)

        model.setLocation(SavedLocation(city: city("Toronto", "CA")))
        XCTAssertEqual(model.settings.method, .moonsightingCommittee)
        XCTAssertEqual(model.settings.madhab, .shafi)

        model.updateSettings { $0.method = .isna }
        model.setLocation(SavedLocation(city: city("Dubai", "AE")))
        XCTAssertEqual(model.settings.method, .isna, "a customized method survives a move")

        model.resetSettings()
        XCTAssertEqual(model.settings.method, .dubai)
        XCTAssertFalse(model.settingsCustomized)
    }

    func testEverythingPersists() {
        let model = AppModel(defaults: defaults)
        model.setLocation(SavedLocation(city: city("Oslo", "NO")))
        model.updateSettings { $0.offsetMinutes[.fajr] = -5; $0.hijriOffsetDays = 1 }
        model.updateAlerts { $0.reminderMinutes = 10; $0.modes[.isha] = .silent }

        let reloaded = AppModel(defaults: defaults)
        XCTAssertEqual(reloaded.location, model.location)
        XCTAssertEqual(reloaded.settings, model.settings)
        XCTAssertEqual(reloaded.alerts, model.alerts)
        XCTAssertTrue(reloaded.settingsCustomized)
    }

    func testFirstLaunchPicksACityInTheDeviceZone() {
        let model = AppModel(defaults: defaults)
        XCTAssertNotNil(TimeZone(identifier: model.location.place.timeZoneId))
        XCTAssertFalse(model.location.isCurrentLocation)
    }
}
