import Foundation
import XCTest
@testable import PrayerCore

/// Runs the shared fixtures in /shared/testdata through PrayerCalculator. The
/// Android suite reads the same files, so the two apps are held to the same numbers.
final class GoldenDataTests: XCTestCase {
    private let testdata = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // PrayerCoreTests
        .deletingLastPathComponent() // Tests
        .deletingLastPathComponent() // PrayerCore
        .deletingLastPathComponent() // ios
        .deletingLastPathComponent() // repo root
        .appendingPathComponent("shared/testdata")

    func testAdhanTestdataMatchesPublishedTimetables() throws {
        let methods: [String: PrayerCore.Method] = [
            "MoonsightingCommittee": .moonsightingCommittee,
            "UmmAlQura": .ummAlQura,
            "Dubai": .dubai,
            "Qatar": .qatar,
            "Kuwait": .kuwait,
            "Singapore": .singapore,
            "Turkey": .turkey,
        ]
        let rules: [String: PrayerCore.HighLatitudeRule] = [
            "MiddleOfTheNight": .middleOfTheNight,
            "SeventhOfTheNight": .seventhOfTheNight,
            "TwilightAngle": .twilightAngle,
        ]
        let dir = testdata.appendingPathComponent("adhan")
        let files = try FileManager.default.contentsOfDirectory(atPath: dir.path)
            .filter { $0.hasSuffix(".json") }
            .sorted()
        var checked = 0
        for file in files {
            let json = try object(dir.appendingPathComponent(file))
            let params = json["params"] as! [String: Any]
            guard let method = methods[params["method"] as! String] else { continue } // Tehran: see README
            let place = Place(
                name: file,
                latitude: params["latitude"] as! Double,
                longitude: params["longitude"] as! Double,
                timeZoneId: params["timezone"] as! String
            )
            let settings = PrayerSettings(
                method: method,
                madhab: params["madhab"] as! String == "Hanafi" ? .hanafi : .shafi,
                highLatitudeRule: rules[params["highLatitudeRule"] as! String]!
            )
            let variance = json["variance"] as? Int ?? 0
            for row in json["times"] as! [[String: String]] {
                let date = CivilDate(row["date"]!)!
                let day = PrayerCalculator.day(at: place, on: date, settings: settings)
                for prayer in Prayer.allCases {
                    assertWithin(variance, clockMinutes(row[prayer.rawValue]!), day, prayer, "\(file) \(date) \(prayer)")
                }
                checked += 1
            }
        }
        XCTAssertGreaterThan(checked, 400, "expected the full Adhan data set")
    }

    func testIndependentReferenceTimesAgree() throws {
        let json = try object(testdata.appendingPathComponent("reference-praytimes.json"))
        let tolerance = json["toleranceMinutes"] as! Int
        let cases = json["cases"] as! [[String: Any]]
        for c in cases {
            let id = c["id"] as! String
            let place = Place(
                name: id,
                latitude: c["latitude"] as! Double,
                longitude: c["longitude"] as! Double,
                timeZoneId: c["timeZone"] as! String
            )
            let day = PrayerCalculator.day(at: place, on: CivilDate(c["date"] as! String)!, settings: settings(c))
            let expected = c["expected"] as! [String: String]
            for prayer in Prayer.allCases {
                assertWithin(tolerance, clockMinutes(expected[prayer.rawValue]!), day, prayer, "\(id) \(prayer)")
            }
        }
        XCTAssertGreaterThanOrEqual(cases.count, 20)
    }

    /// Method ids are Method raw values, or "custom:<fajr>:<isha>".
    private func settings(_ c: [String: Any]) -> PrayerSettings {
        let id = c["method"] as! String
        let madhab: Madhab = c["madhab"] as! String == "hanafi" ? .hanafi : .shafi
        if id.hasPrefix("custom:") {
            let parts = id.split(separator: ":").map(String.init)
            return PrayerSettings(
                method: .custom,
                madhab: madhab,
                customAngles: CustomAngles(fajrAngle: Double(parts[1])!, ishaAngle: Double(parts[2])!)
            )
        }
        return PrayerSettings(method: PrayerCore.Method(rawValue: id)!, madhab: madhab)
    }

    private func object(_ url: URL) throws -> [String: Any] {
        try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
    }

    /// Minutes since midnight for "17:06" or "5:06 PM".
    private func clockMinutes(_ text: String) -> Int {
        let parts = text.split(separator: " ")
        let hm = parts[0].split(separator: ":").map { Int($0)! }
        guard parts.count == 2 else { return hm[0] * 60 + hm[1] }
        let hour = hm[0] % 12 + (parts[1] == "PM" ? 12 : 0)
        return hour * 60 + hm[1]
    }

    private func assertWithin(_ minutes: Int, _ expected: Int, _ day: DaySchedule, _ prayer: Prayer, _ label: String) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = day.place.timeZone
        let c = calendar.dateComponents([.hour, .minute], from: day[prayer])
        let actual = c.hour! * 60 + c.minute!
        let diff = abs(actual - expected)
        // Guard against midnight wrap (e.g. 23:59 vs 00:00).
        XCTAssertLessThanOrEqual(min(diff, 1440 - diff), minutes, "\(label): expected \(expected / 60):\(expected % 60) ±\(minutes) min, got \(c.hour!):\(c.minute!)")
    }
}
