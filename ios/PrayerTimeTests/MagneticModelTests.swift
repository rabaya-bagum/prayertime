import Foundation
import PrayerCore
import XCTest
@testable import PrayerTime

final class MagneticModelTests: XCTestCase {
    private let model = MagneticModel.shared

    func testLoadsTheBundledCoefficients() {
        XCTAssertEqual(model.epoch, 2025.0)
        XCTAssertEqual(model.name, "WMM-2025")
    }

    /// NOAA's official WMM2025 test values (WMM2025_TestValues.txt, shipped with the model).
    func testMatchesNOAATestValues() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "WMM2025_TestValues", withExtension: "txt"))
        let rows = try String(contentsOf: url, encoding: .utf8)
            .split(whereSeparator: \.isNewline)
            .filter { !$0.hasPrefix("#") }
            .map { $0.split(separator: " ").compactMap { Double($0) } }
        XCTAssertGreaterThanOrEqual(rows.count, 12)

        for v in rows {
            let (year, alt, lat, lon, decl) = (v[0], v[1], v[2], v[3], v[4])
            let (x, y, z) = model.field(latitude: lat, longitude: lon, altitudeKm: alt, decimalYear: year)
            let label = "\(year) \(lat),\(lon) @\(alt)km"
            // Components to 0.1 nT; the file prints declination to 0.01°.
            XCTAssertEqual(x, v[7], accuracy: 0.1, "X \(label)")
            XCTAssertEqual(y, v[8], accuracy: 0.1, "Y \(label)")
            XCTAssertEqual(z, v[9], accuracy: 0.1, "Z \(label)")
            XCTAssertEqual(model.declination(latitude: lat, longitude: lon, altitudeKm: alt, decimalYear: year),
                           decl, accuracy: 0.01, "D \(label)")
        }
    }

    func testTorontoDeclinationIsAboutTenWest() {
        // Sanity check a city users will hit: Toronto's declination is roughly −10°.
        let d = model.declination(latitude: 43.65, longitude: -79.38, decimalYear: 2026.8)
        XCTAssertEqual(d, -10, accuracy: 1.5)
    }

    func testDecimalYear() {
        let utc = TimeZone(identifier: "UTC")!
        XCTAssertEqual(MagneticModel.decimalYear(CivilDate(year: 2025, month: 1, day: 1).at(hour: 0, in: utc)), 2025.0)
        XCTAssertEqual(MagneticModel.decimalYear(CivilDate(year: 2026, month: 7, day: 2).at(hour: 12, in: utc)), 2026.5, accuracy: 0.002)
    }
}

