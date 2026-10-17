import Foundation
import XCTest
@testable import PrayerCore

/// Mirrors android/core's NotificationsTest.
final class NotificationsTests: XCTestCase {
    private let toronto = Place(name: "Toronto", latitude: 43.6532, longitude: -79.3832, timeZoneId: "America/Toronto")
    private var inputs: ScheduleInputs { ScheduleInputs(place: toronto, settings: PrayerSettings(method: .isna)) }
    // After Dhuhr (13:2x EDT).
    private var now: Date { CivilDate(year: 2026, month: 5, day: 15).at(hour: 14, in: toronto.timeZone) }

    private func days(_ n: Double) -> TimeInterval { n * 86_400 }

    private func prefs(_ overrides: [Prayer: PrayerAlert]) -> NotificationPreferences {
        NotificationPreferences(alerts: NotificationPreferences.defaultAlerts.merging(overrides) { $1 })
    }

    private func with(_ notifications: NotificationPreferences) -> ScheduleInputs {
        ScheduleInputs(place: toronto, settings: inputs.settings, notifications: notifications)
    }

    func testDefaultAlertsAreFiveAdhansAndNoSunrise() {
        let alerts = AlertPlanner.alerts(inputs, now: now, until: now.addingTimeInterval(days(2)))
        XCTAssertTrue(alerts.allSatisfy { $0.kind == .adhan && $0.sound == PrayerAlert.defaultSound })
        XCTAssertFalse(alerts.contains { $0.prayer == .sunrise })
        XCTAssertEqual(alerts.count, 10)
        XCTAssertEqual(alerts.first?.prayer, .asr)
        XCTAssertEqual(alerts.map(\.time), alerts.map(\.time).sorted())
    }

    func testModesAndRemindersPerPrayer() {
        let p = prefs([
            .fajr: PrayerAlert(mode: .adhan, sound: "madinah", reminderMinutesBefore: 20),
            .asr: PrayerAlert(mode: .silent),
            .isha: PrayerAlert(mode: .off),
        ])
        let alerts = AlertPlanner.alerts(with(p), now: now, until: now.addingTimeInterval(days(1)))
        let fajr = alerts.first { $0.kind == .adhan && $0.prayer == .fajr }!
        let reminder = alerts.first { $0.kind == .reminder && $0.prayer == .fajr }!
        XCTAssertEqual(fajr.sound, "madinah")
        XCTAssertEqual(fajr.time.timeIntervalSince(reminder.time), 20 * 60)
        XCTAssertNil(alerts.first { $0.kind == .silent && $0.prayer == .asr }!.sound)
        XCTAssertFalse(alerts.contains { $0.prayer == .isha })
    }

    func testIosWindowFillsSixtyThreeSlotsPlusKeepAlive() {
        let plan = NotificationWindowPlanner.plan(inputs, now: now)
        XCTAssertEqual(plan.count, 64)
        let keepAlive = plan.filter { $0.kind == .keepAlive }
        XCTAssertEqual(keepAlive.count, 1)
        let lastAdhan = plan.filter { $0.kind == .adhan }.map(\.time).max()!
        XCTAssertEqual(keepAlive[0].time, lastAdhan.addingTimeInterval(-NotificationWindowPlanner.keepAliveLead))
        XCTAssertTrue((12...13).contains(Int(lastAdhan.timeIntervalSince(now) / 86_400)))
        XCTAssertEqual(Set(plan.map(\.identifier)).count, plan.count)
    }

    func testRemindersHalveTheIosWindow() {
        let all = Dictionary(uniqueKeysWithValues: Prayer.allCases.map {
            ($0, $0 == .sunrise ? PrayerAlert(mode: .off) : PrayerAlert(mode: .adhan, reminderMinutesBefore: 15))
        })
        let plan = NotificationWindowPlanner.plan(with(NotificationPreferences(alerts: all)), now: now)
        let last = plan.filter { $0.kind != .keepAlive }.map(\.time).max()!
        XCTAssertTrue((6...7).contains(Int(last.timeIntervalSince(now) / 86_400)))
    }

    func testSparsePreferencesStopAtTheHorizon() {
        let onlyFajr = NotificationPreferences(alerts: Dictionary(uniqueKeysWithValues: Prayer.allCases.map {
            ($0, PrayerAlert(mode: $0 == .fajr ? .silent : .off))
        }))
        let plan = NotificationWindowPlanner.plan(with(onlyFajr), now: now)
        XCTAssertEqual(plan.count, 31)
        XCTAssertTrue(plan.allSatisfy { $0.time > now })

        let allOff = NotificationPreferences(alerts: Dictionary(uniqueKeysWithValues: Prayer.allCases.map { ($0, PrayerAlert(mode: .off)) }))
        XCTAssertTrue(NotificationWindowPlanner.plan(with(allOff), now: now).isEmpty)
    }

    func testDiffOnlyTouchesChangedAlerts() {
        let pending = Set(NotificationWindowPlanner.plan(inputs, now: now).map(\.identifier))
        let later = NotificationWindowPlanner.plan(inputs, now: now.addingTimeInterval(6 * 3600))
        XCTAssertLessThanOrEqual(NotificationWindowPlanner.diff(pending: pending, desired: later).add.count, 4)

        let changed = NotificationWindowPlanner.plan(with(prefs([.maghrib: PrayerAlert(mode: .adhan, sound: "alaqsa")])), now: now)
        let diff = NotificationWindowPlanner.diff(pending: pending, desired: changed)
        XCTAssertTrue(diff.add.allSatisfy { $0.prayer == .maghrib })
        XCTAssertEqual(diff.add.count, diff.remove.filter { $0.contains("|maghrib|") }.count)
    }

    func testTorontoWindowAcrossFallBackDst() {
        let beforeFallBack = CivilDate(year: 2026, month: 10, day: 30).at(hour: 12, in: toronto.timeZone)
        let adhans = NotificationWindowPlanner.plan(inputs, now: beforeFallBack).filter { $0.kind == .adhan }
        let perDate = Dictionary(grouping: adhans) { $0.date! }.mapValues(\.count)
        XCTAssertEqual(perDate[CivilDate(year: 2026, month: 11, day: 1)], 5)
        XCTAssertEqual(perDate[CivilDate(year: 2026, month: 11, day: 2)], 5)
    }
}
