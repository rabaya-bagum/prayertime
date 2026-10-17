import Foundation
import PrayerCore
import UserNotifications
import XCTest
@testable import PrayerNotifications

private final class FakeCenter: NotificationCenterClient {
    var requests: [String: UNNotificationRequest] = [:]
    var foreign: Set<String> = []
    var addCount = 0
    var removeCount = 0

    func pendingIdentifiers() async -> Set<String> { Set(requests.keys).union(foreign) }

    func add(_ request: UNNotificationRequest) async throws {
        requests[request.identifier] = request
        addCount += 1
    }

    func removePending(_ identifiers: [String]) {
        identifiers.forEach { requests[$0] = nil }
        removeCount += identifiers.count
    }
}

@MainActor
final class NotificationSchedulerTests: XCTestCase {
    private let toronto = Place(name: "Toronto", latitude: 43.6532, longitude: -79.3832, timeZoneId: "America/Toronto")
    private var inputs: ScheduleInputs { ScheduleInputs(place: toronto, settings: PrayerSettings(method: .isna)) }

    func testRefreshFillsSixtyFourSlotsWithAbsoluteTriggers() async throws {
        let center = FakeCenter()
        let now = Date()
        try await NotificationScheduler(center: center).refresh(inputs, now: now)
        XCTAssertEqual(center.requests.count, 64)

        let plan = NotificationWindowPlanner.plan(inputs, now: now)
        for alert in plan {
            let request = try XCTUnwrap(center.requests[alert.identifier])
            let trigger = try XCTUnwrap(request.trigger as? UNCalendarNotificationTrigger)
            XCTAssertEqual(trigger.dateComponents.date, alert.time, alert.identifier)
            XCTAssertFalse(trigger.repeats)
        }
    }

    func testRefreshIsIdempotentAndLeavesOtherNotificationsAlone() async throws {
        let center = FakeCenter()
        center.foreign = ["app-update-reminder"]
        let scheduler = NotificationScheduler(center: center)
        let now = Date()
        try await scheduler.refresh(inputs, now: now)
        let second = try await scheduler.refresh(inputs, now: now)
        XCTAssertTrue(second.add.isEmpty)
        XCTAssertTrue(second.remove.isEmpty)
        XCTAssertEqual(center.removeCount, 0)
    }

    func testNilInputsClearPrayerNotifications() async throws {
        let center = FakeCenter()
        let scheduler = NotificationScheduler(center: center)
        try await scheduler.refresh(inputs)
        let diff = try await scheduler.refresh(nil)
        XCTAssertEqual(diff.remove.count, 64)
        XCTAssertTrue(center.requests.isEmpty)
    }

    func testContentPerKind() async throws {
        var prefs = NotificationPreferences()
        prefs.alerts[.fajr] = PrayerAlert(mode: .adhan, sound: "madinah", reminderMinutesBefore: 15)
        prefs.alerts[.asr] = PrayerAlert(mode: .silent)
        let center = FakeCenter()
        try await NotificationScheduler(center: center).refresh(
            ScheduleInputs(place: toronto, settings: PrayerSettings(method: .isna), notifications: prefs)
        )
        let contents = center.requests.values.map(\.content)
        let byKind = Dictionary(grouping: contents) { $0.userInfo[NotificationScheduler.UserInfoKey.kind] as! String }

        let fajrAdhan = try XCTUnwrap(byKind["adhan"]?.first { $0.title == "Fajr" })
        XCTAssertEqual(fajrAdhan.body, "It's time for Fajr")
        XCTAssertEqual(fajrAdhan.interruptionLevel, .timeSensitive)
        XCTAssertNotNil(fajrAdhan.sound)

        let asr = try XCTUnwrap(byKind["silent"]?.first)
        XCTAssertEqual(asr.title, "Asr")
        XCTAssertNil(asr.sound)

        let reminder = try XCTUnwrap(byKind["reminder"]?.first)
        XCTAssertEqual(reminder.title, "Fajr")
        XCTAssertTrue(reminder.body.hasPrefix("Fajr at "))

        let keepAlive = try XCTUnwrap(byKind["keep_alive"]?.first)
        XCTAssertTrue(keepAlive.body.contains("scheduled until"))
    }

    func testPlannerMatchesAndroidIdentifierFormat() {
        let alert = PlannedAlert(
            kind: .adhan, time: Date(timeIntervalSince1970: 1_778_923_200), prayer: .fajr,
            date: CivilDate(year: 2026, month: 5, day: 16), sound: "makkah"
        )
        XCTAssertEqual(alert.identifier, "adhan|2026-05-16|fajr|1778923200|makkah")
        XCTAssertEqual(PlannedAlert(kind: .keepAlive, time: Date(timeIntervalSince1970: 60)).identifier, "keep_alive|-|-|60|-")
    }
}
