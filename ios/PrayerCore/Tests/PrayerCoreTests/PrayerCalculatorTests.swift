import Foundation
import XCTest
@testable import PrayerCore

/// Mirrors android/core's PrayerCalculatorTest case for case.
final class PrayerCalculatorTests: XCTestCase {
    private let toronto = Place(name: "Toronto", latitude: 43.6532, longitude: -79.3832, timeZoneId: "America/Toronto")
    private let makkah = Place(name: "Makkah", latitude: 21.4225, longitude: 39.8262, timeZoneId: "Asia/Riyadh")
    private let oslo = Place(name: "Oslo", latitude: 59.9139, longitude: 10.7522, timeZoneId: "Europe/Oslo")
    private let reykjavik = Place(name: "Reykjavik", latitude: 64.1466, longitude: -21.9426, timeZoneId: "Atlantic/Reykjavik")
    private let tromso = Place(name: "Tromsø", latitude: 69.6492, longitude: 18.9553, timeZoneId: "Europe/Oslo")
    private let kathmandu = Place(name: "Kathmandu", latitude: 27.7172, longitude: 85.3240, timeZoneId: "Asia/Kathmandu")
    private let kiritimati = Place(name: "Kiritimati", latitude: 1.8721, longitude: -157.4278, timeZoneId: "Pacific/Kiritimati")
    private let apia = Place(name: "Apia", latitude: -13.8333, longitude: -171.7667, timeZoneId: "Pacific/Apia")
    private let sydney = Place(name: "Sydney", latitude: -33.8688, longitude: 151.2093, timeZoneId: "Australia/Sydney")

    private let mwl = PrayerSettings(method: .muslimWorldLeague)

    private func minutes(_ from: Date, _ to: Date) -> Int { Int(to.timeIntervalSince(from) / 60) }

    func testEveryDayOfTheYearIsOrderedForManyPlacesAndMethods() {
        let places = [toronto, makkah, oslo, reykjavik, tromso, kathmandu, kiritimati, apia, sydney]
        let methods: [PrayerCore.Method] = [.muslimWorldLeague, .isna, .moonsightingCommittee, .ummAlQura]
        for place in places {
            for method in methods {
                var date = CivilDate(year: 2026, month: 1, day: 1)
                while date.year == 2026 {
                    let day = PrayerCalculator.day(at: place, on: date, settings: PrayerSettings(method: method))
                    let times = Prayer.allCases.map { day[$0] }
                    XCTAssertEqual(times.sorted(), times, "\(place.name) \(method) \(date) out of order")
                    XCTAssertEqual(CivilDate(day[.dhuhr], in: place.timeZone), date, "\(place.name) \(date) Dhuhr on wrong day")
                    date = date.adding(days: 1)
                }
            }
        }
    }

    func testTorontoFajrSpreadsAbout48MinutesAcrossMasjidConventions() {
        let date = CivilDate(year: 2026, month: 5, day: 15)
        func fajr(_ angle: Double) -> Date {
            PrayerCalculator.day(
                at: toronto, on: date,
                settings: PrayerSettings(method: .custom, customAngles: CustomAngles(fajrAngle: angle, ishaAngle: angle))
            )[.fajr]
        }
        let spread = minutes(fajr(18), fajr(12))
        XCTAssertTrue((40...56).contains(spread), "12° vs 18° spread was \(spread) min")
        XCTAssertTrue((fajr(18)...fajr(12)).contains(fajr(15)))
    }

    func testHanafiAsrIsLater() {
        let date = CivilDate(year: 2026, month: 6, day: 1)
        let shafi = PrayerCalculator.day(at: toronto, on: date, settings: mwl)[.asr]
        var hanafiSettings = mwl
        hanafiSettings.madhab = .hanafi
        let hanafi = PrayerCalculator.day(at: toronto, on: date, settings: hanafiSettings)[.asr]
        XCTAssertTrue((30...90).contains(minutes(shafi, hanafi)))
    }

    func testDstChangeoverMovesLocalClockButNotTheSun() {
        let before = PrayerCalculator.day(at: toronto, on: CivilDate(year: 2026, month: 3, day: 7), settings: mwl)
        let after = PrayerCalculator.day(at: toronto, on: CivilDate(year: 2026, month: 3, day: 8), settings: mwl)
        // Solar noon drifts by seconds per day, so the UTC instants barely move...
        XCTAssertLessThan(abs(after[.dhuhr].timeIntervalSince(before[.dhuhr]) - 86_400), 120)
        // ...while the wall clock jumps forward an hour.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = toronto.timeZone
        let b = calendar.dateComponents([.hour, .minute], from: before[.dhuhr])
        let a = calendar.dateComponents([.hour, .minute], from: after[.dhuhr])
        let jump = (a.hour! * 60 + a.minute!) - (b.hour! * 60 + b.minute!)
        XCTAssertTrue((59...61).contains(jump))
    }

    func testOsloSummerUsesSeventhOfTheNightAutomatically() {
        let day = PrayerCalculator.day(at: oslo, on: CivilDate(year: 2026, month: 6, day: 21), settings: mwl)
        XCTAssertEqual(day.adjustment, .calculated(highLatitudeRule: .seventhOfTheNight))
        // Twilight never ends at 18°, so Isha is capped at sunset + 1/7 of the night.
        let tomorrowSunrise = PrayerCalculator.day(at: oslo, on: CivilDate(year: 2026, month: 6, day: 22), settings: mwl)[.sunrise]
        let night = tomorrowSunrise.timeIntervalSince(day[.maghrib])
        XCTAssertLessThanOrEqual(day[.isha].timeIntervalSince(day[.maghrib]), night / 7 + 60)
    }

    func testMidnightSunAndPolarNightFallBackToNearestLatitude() {
        for date in [CivilDate(year: 2026, month: 6, day: 21), CivilDate(year: 2026, month: 12, day: 21)] {
            let day = PrayerCalculator.day(at: tromso, on: date, settings: mwl)
            guard case let .polarFallback(latitudeUsed, _) = day.adjustment else {
                return XCTFail("Tromsø \(date) should fall back, got \(day.adjustment)")
            }
            XCTAssertTrue(latitudeUsed < tromso.latitude && latitudeUsed > 60)
        }
        // Outside those seasons Tromsø calculates normally.
        let equinox = PrayerCalculator.day(at: tromso, on: CivilDate(year: 2026, month: 3, day: 20), settings: mwl)
        guard case .calculated = equinox.adjustment else { return XCTFail("Tromsø equinox fell back") }
    }

    func testSouthernHemisphereLatitudeRuleIsMiddleOfTheNight() {
        let day = PrayerCalculator.day(at: sydney, on: CivilDate(year: 2026, month: 6, day: 21), settings: mwl)
        XCTAssertEqual(day.adjustment, .calculated(highLatitudeRule: .middleOfTheNight))
    }

    func testUmmAlQuraIshaIs120MinutesInRamadanFollowingAdjustedHijriDate() {
        let settings = PrayerSettings(method: .ummAlQura)
        func ishaGap(_ date: CivilDate, _ s: PrayerSettings) -> Int {
            let day = PrayerCalculator.day(at: makkah, on: date, settings: s)
            return minutes(day[.maghrib], day[.isha])
        }
        let midRamadan = CivilDate(year: 2026, month: 3, day: 1)
        XCTAssertTrue(HijriDate.of(midRamadan).isRamadan)
        XCTAssertEqual(ishaGap(midRamadan, settings), 120)
        XCTAssertEqual(ishaGap(CivilDate(year: 2026, month: 4, day: 15), settings), 90)

        // 1 Ramadan 1447 by Umm al-Qura. With a -1 day sighting offset it is still Sha'ban.
        let firstDay = CivilDate(year: 2026, month: 2, day: 18)
        XCTAssertEqual(HijriDate.of(firstDay), HijriDate(year: 1447, month: 9, day: 1))
        XCTAssertEqual(ishaGap(firstDay, settings), 120)
        var offset = settings
        offset.hijriOffsetDays = -1
        XCTAssertEqual(ishaGap(firstDay, offset), 90)
    }

    func testUserOffsetsAndImsak() {
        let date = CivilDate(year: 2026, month: 5, day: 15)
        let plain = PrayerCalculator.day(at: toronto, on: date, settings: mwl)
        var offsetSettings = mwl
        offsetSettings.offsetMinutes = [.asr: 3, .isha: -2]
        let shifted = PrayerCalculator.day(at: toronto, on: date, settings: offsetSettings)
        XCTAssertEqual(plain[.asr].addingTimeInterval(180), shifted[.asr])
        XCTAssertEqual(plain[.isha].addingTimeInterval(-120), shifted[.isha])
        XCTAssertEqual(plain[.fajr], shifted[.fajr])
        XCTAssertEqual(plain[.fajr].addingTimeInterval(-600), plain.imsak)
        XCTAssertGreaterThan(plain.lastThirdOfTheNight, plain.middleOfTheNight)
    }

    func testUpcomingCrossesMidnightInOrder() {
        let lateEvening = CivilDate(year: 2026, month: 5, day: 15).at(hour: 23, minute: 30, in: toronto.timeZone)
        let next = PrayerCalculator.upcoming(at: toronto, settings: mwl, from: lateEvening, count: 3)
        XCTAssertEqual(next.map(\.prayer), [.fajr, .sunrise, .dhuhr])
        XCTAssertTrue(next.allSatisfy { $0.date == CivilDate(year: 2026, month: 5, day: 16) })
    }

    func testUpcomingIsStrictlyIncreasingAcrossDstAndTheDateLine() {
        let cases: [(Place, Date)] = [
            (toronto, CivilDate(year: 2026, month: 10, day: 28).at(hour: 0, minute: 30, in: toronto.timeZone)), // DST ends Nov 1
            (kiritimati, CivilDate(year: 2026, month: 1, day: 1).at(hour: 0, minute: 30, in: kiritimati.timeZone)),
            (apia, CivilDate(year: 2026, month: 1, day: 1).at(hour: 0, minute: 30, in: apia.timeZone)),
            (kathmandu, CivilDate(year: 2026, month: 1, day: 1).at(hour: 0, minute: 30, in: kathmandu.timeZone)),
        ]
        for (place, from) in cases {
            let events = PrayerCalculator.upcoming(at: place, settings: mwl, from: from, count: 60)
            XCTAssertEqual(events.count, 60)
            XCTAssertGreaterThan(events.first!.time, from)
            XCTAssertTrue(zip(events, events.dropFirst()).allSatisfy { $0.time < $1.time }, place.name)
            // Exactly six events per day, so 60 events cover 10 consecutive days.
            XCTAssertEqual(Set(events.map(\.date)).count, 10, place.name)
        }
    }

    func testQiblaFromTorontoPointsNortheast() {
        let bearing = PrayerCalculator.qiblaBearing(latitude: toronto.latitude, longitude: toronto.longitude)
        XCTAssertEqual(bearing, 54.6, accuracy: 0.5)
    }

    func testCountryDefaults() {
        XCTAssertEqual(Defaults.settings(forCountry: "ca").method, .moonsightingCommittee)
        XCTAssertEqual(Defaults.settings(forCountry: "PK"), PrayerSettings(method: .karachi, madhab: .hanafi))
        XCTAssertEqual(Defaults.settings(forCountry: "SA").method, .ummAlQura)
        XCTAssertEqual(Defaults.settings(forCountry: nil).method, .muslimWorldLeague)
    }
}
