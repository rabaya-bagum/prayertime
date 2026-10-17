import Foundation
import PrayerCore
import XCTest
@testable import PrayerTime

final class AlertPlannerTests: XCTestCase {
    private let toronto = Place(name: "Toronto", latitude: 43.6532, longitude: -79.3832, timeZoneId: "America/Toronto")
    private let tromso = Place(name: "Tromsø", latitude: 69.6492, longitude: 18.9553, timeZoneId: "Europe/Oslo")
    private let settings = PrayerSettings(method: .moonsightingCommittee)
    private let now = CivilDate(year: 2026, month: 3, day: 1).at(hour: 12, in: TimeZone(identifier: "America/Toronto")!)

    func testFillsExactlyTheSystemLimitAndEndsWithKeepAlive() {
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: AlertPreferences(), from: now)
        XCTAssertEqual(plan.count, AlertPlanner.systemLimit)
        XCTAssertEqual(plan.last?.kind, .keepAlive)
        XCTAssertEqual(plan.dropLast().filter { $0.kind == .keepAlive }.count, 0)
    }

    func testAlertsAreFutureAndInOrder() {
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: AlertPreferences(), from: now)
        XCTAssertTrue(plan.allSatisfy { $0.time > now })
        XCTAssertEqual(plan.map(\.time), plan.map(\.time).sorted())
    }

    func testKeepAliveTakesTheFirstPrayerThatDidNotFit() {
        let full = AlertPlanner.plan(at: toronto, settings: settings, preferences: AlertPreferences(), from: now, limit: 200)
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: AlertPreferences(), from: now, limit: 10)
        XCTAssertEqual(Array(plan.prefix(9)), Array(full.prefix(9)))
        XCTAssertEqual(plan[9].time, full[9].time)
        XCTAssertEqual(plan[9].prayer, full[9].prayer)
    }

    func testFiveAdhansADayFillAboutTwelveDays() {
        // SPEC: five adhans a day fill the 64 slots in about 12 days.
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: AlertPreferences(), from: now)
        let days = plan.last!.time.timeIntervalSince(now) / 86_400
        XCTAssertEqual(days, 12.6, accuracy: 1)
        XCTAssertFalse(plan.contains { $0.prayer == .sunrise }, "sunrise is off by default")
    }

    func testRemindersPrecedeTheirAdhanAndHalveTheWindow() {
        var prefs = AlertPreferences()
        prefs.reminderMinutes = 15
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: prefs, from: now)
        let adhans = plan.filter { $0.kind == .adhan }
        for adhan in adhans {
            let reminder = plan.first { $0.kind == .reminder(minutesBefore: 15) && $0.prayer == adhan.prayer
                && adhan.time.timeIntervalSince($0.time) == 15 * 60 }
            XCTAssertNotNil(reminder, "no reminder for \(adhan.prayer) at \(adhan.time)")
        }
        let days = plan.last!.time.timeIntervalSince(now) / 86_400
        XCTAssertEqual(days, 6.3, accuracy: 1)
    }

    func testReminderAlreadyPastIsDroppedButAdhanKept() {
        var prefs = AlertPreferences()
        prefs.reminderMinutes = 60
        let dhuhr = PrayerCalculator.day(at: toronto, on: CivilDate(year: 2026, month: 3, day: 1), settings: settings)[.dhuhr]
        let justBefore = dhuhr.addingTimeInterval(-10 * 60)
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: prefs, from: justBefore)
        XCTAssertEqual(plan.first?.kind, .adhan)
        XCTAssertEqual(plan.first?.time, dhuhr)
    }

    func testModes() {
        var prefs = AlertPreferences()
        prefs.modes = [.fajr: .silent]
        let plan = AlertPlanner.plan(at: toronto, settings: settings, preferences: prefs, from: now)
        XCTAssertTrue(plan.dropLast().allSatisfy { $0.prayer == .fajr && !$0.playsSound })
        XCTAssertEqual(plan.count, AlertPlanner.horizonDays + 1, "one Fajr a day for the horizon, then keep-alive")

        prefs.modes = [:]
        XCTAssertEqual(AlertPlanner.plan(at: toronto, settings: settings, preferences: prefs, from: now), [])
    }

    func testPolarNightStillPlansOrderedAlerts() {
        let midwinter = CivilDate(year: 2026, month: 12, day: 21).at(hour: 0, in: tromso.timeZone)
        var prefs = AlertPreferences()
        prefs.modes[.sunrise] = .sound
        let plan = AlertPlanner.plan(at: tromso, settings: PrayerSettings(method: .muslimWorldLeague), preferences: prefs, from: midwinter)
        XCTAssertEqual(plan.count, AlertPlanner.systemLimit)
        XCTAssertEqual(plan.map(\.time), plan.map(\.time).sorted())
    }
}
