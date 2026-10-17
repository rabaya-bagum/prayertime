import Foundation
import PrayerCore
import XCTest
@testable import PrayerTime

final class CityDatabaseTests: XCTestCase {
    private let db = CityDatabase.shared

    func testLoadsTheWholeFileWithValidZones() {
        XCTAssertGreaterThan(db.cities.count, 30_000)
        for city in db.cities {
            XCTAssertNotNil(TimeZone(identifier: city.timeZoneId), "\(city.name): \(city.timeZoneId)")
        }
    }

    func testMostPopulousPrefixMatchComesFirst() {
        let london = db.search("london")
        XCTAssertEqual(london.first?.countryCode, "GB")
        XCTAssertTrue(london.contains { $0.countryCode == "CA" && $0.region == "Ontario" })
    }

    func testQualifierNarrowsByRegionCountryOrCode() {
        XCTAssertEqual(db.search("london, ontario").first?.countryCode, "CA")
        XCTAssertEqual(db.search("london, ca").first?.region, "Ontario")
        XCTAssertEqual(db.search("london, canada").first?.region, "Ontario")
    }

    func testSearchIgnoresCaseAndDiacritics() {
        XCTAssertEqual(db.search("tromso").first?.name, "Tromsø")
        XCTAssertEqual(db.search("MONTREAL").first?.name, "Montréal")
    }

    func testEmptyQueryReturnsNothing() {
        XCTAssertEqual(db.search("  "), [])
    }

    func testNearestCityGivesTheZoneForAGPSFix() {
        // Mississauga-ish fix: nearest city is in Toronto's zone.
        XCTAssertEqual(db.nearest(latitude: 43.59, longitude: -79.64)?.timeZoneId, "America/Toronto")
        // Just across the date line from Fiji's Taveuni.
        XCTAssertEqual(db.nearest(latitude: -16.85, longitude: 179.99)?.countryCode, "FJ")
        // Kathmandu valley: +5:45.
        XCTAssertEqual(db.nearest(latitude: 27.68, longitude: 85.31)?.timeZoneId, "Asia/Kathmandu")
    }

    func testGPSLocationIsRoundedAndUsesNearestCityZone() {
        let city = db.nearest(latitude: 43.6512, longitude: -79.3471)!
        let location = SavedLocation(fixLatitude: 43.6512, longitude: -79.3471, nearest: city)
        XCTAssertEqual(location.place.latitude, 43.65)
        XCTAssertEqual(location.place.longitude, -79.35)
        XCTAssertEqual(location.place.timeZoneId, "America/Toronto")
        XCTAssertTrue(location.isCurrentLocation)
    }
}
