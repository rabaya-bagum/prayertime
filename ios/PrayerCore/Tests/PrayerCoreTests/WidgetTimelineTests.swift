import Foundation
import XCTest
@testable import PrayerCore

final class WidgetTimelineTests: XCTestCase {
    private let toronto = Place(name: "Toronto", latitude: 43.6532, longitude: -79.3832, timeZoneId: "America/Toronto")
    private var configuration: SharedConfiguration {
        SharedConfiguration(place: toronto, subtitle: "Ontario, Canada", settings: PrayerSettings(method: .isna))
    }

    func testEntriesStartNowThenAtEachPrayerAndMidnight() {
        let now = CivilDate(year: 2026, month: 5, day: 15).at(hour: 14, in: toronto.timeZone)
        let entries = WidgetTimeline.entries(configuration, from: now, hours: 36)
        XCTAssertEqual(entries.first?.date, now)
        XCTAssertEqual(entries.map(\.date), entries.map(\.date).sorted())

        // 14:00 → Asr, Maghrib, Isha, midnight, then six times on the 16th and midnight before 02:00 on the 17th.
        let today = PrayerCalculator.day(at: toronto, on: CivilDate(year: 2026, month: 5, day: 15), settings: configuration.settings)
        XCTAssertTrue(entries.contains { $0.date == today[.asr] })
        XCTAssertTrue(entries.contains { $0.date == CivilDate(year: 2026, month: 5, day: 16).at(hour: 0, in: toronto.timeZone) })
        XCTAssertEqual(entries.count, 1 + 3 + 1 + 6 + 1)
    }

    func testEachEntryPointsAtTheNextPrayerAndItsOwnDay() {
        let now = CivilDate(year: 2026, month: 5, day: 15).at(hour: 14, in: toronto.timeZone)
        for entry in WidgetTimeline.entries(configuration, from: now) {
            XCTAssertGreaterThan(entry.next.time, entry.date)
            XCTAssertEqual(entry.day, CivilDate(entry.date, in: toronto.timeZone))
            XCTAssertEqual(entry.times.count, 6)
        }
        // At Asr itself the widget already shows Maghrib.
        let asr = PrayerCalculator.day(at: toronto, on: CivilDate(year: 2026, month: 5, day: 15), settings: configuration.settings)[.asr]
        let atAsr = WidgetTimeline.entries(configuration, from: now).first { $0.date == asr }
        XCTAssertEqual(atAsr?.next.prayer, .maghrib)
    }

    func testAcrossTheDstChangeEveryLocalMidnightIsAnEntry() {
        let now = CivilDate(year: 2026, month: 10, day: 31).at(hour: 22, in: toronto.timeZone)
        let entries = WidgetTimeline.entries(configuration, from: now, hours: 30)
        XCTAssertTrue(entries.contains { $0.date == CivilDate(year: 2026, month: 11, day: 1).at(hour: 0, in: toronto.timeZone) })
        XCTAssertEqual(Set(entries.map(\.date)).count, entries.count, "no duplicate entries")
    }

    func testConfigurationRoundTripsThroughDefaults() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "WidgetTimelineTests"))
        defaults.removePersistentDomain(forName: "WidgetTimelineTests")
        XCTAssertNil(SharedConfiguration.load(from: defaults))
        configuration.save(to: defaults)
        XCTAssertEqual(SharedConfiguration.load(from: defaults), configuration)
    }
}
