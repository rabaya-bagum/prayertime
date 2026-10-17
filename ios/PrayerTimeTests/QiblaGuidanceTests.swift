import Foundation
import PrayerCore
import XCTest
@testable import PrayerTime

final class QiblaGuidanceTests: XCTestCase {
    func testTurnIsTheShortWayRound() {
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 55, trueHeading: 0).turn, 55)
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 55, trueHeading: 100).turn, -45)
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 10, trueHeading: 350).turn, 20, "across north")
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 350, trueHeading: 10).turn, -20, "across north the other way")
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 180, trueHeading: 0).turn, 180, "directly behind turns right")
    }

    func testInstructions() {
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 55, trueHeading: 0).instruction, "Turn right 55°")
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 55, trueHeading: 100).instruction, "Turn left 45°")
        XCTAssertEqual(QiblaGuidance(qiblaBearing: 55, trueHeading: 53).instruction, "Facing the Qibla")
    }

    func testAlignmentHasHysteresis() {
        XCTAssertTrue(QiblaGuidance(qiblaBearing: 55, trueHeading: 52).isAligned)
        XCTAssertFalse(QiblaGuidance(qiblaBearing: 55, trueHeading: 50).isAligned, "5° off doesn't enter")
        XCTAssertTrue(QiblaGuidance(qiblaBearing: 55, trueHeading: 50, wasAligned: true).isAligned, "but doesn't leave either")
        XCTAssertFalse(QiblaGuidance(qiblaBearing: 55, trueHeading: 48, wasAligned: true).isAligned, "7° off leaves")
    }

    func testHeadingQuality() {
        XCTAssertEqual(HeadingQuality(accuracy: -1), .calibrating)
        XCTAssertEqual(HeadingQuality(accuracy: 5), .good(5))
        XCTAssertEqual(HeadingQuality(accuracy: 20), .fair(20))
        XCTAssertEqual(HeadingQuality(accuracy: 40), .poor(40))
        XCTAssertTrue(HeadingQuality(accuracy: -1).needsCalibration)
        XCTAssertTrue(HeadingQuality(accuracy: 40).needsCalibration)
        XCTAssertFalse(HeadingQuality(accuracy: 20).needsCalibration)
    }

    func testDeclinationText() {
        XCTAssertEqual((-10.24).declinationText, "10.2° W")
        XCTAssertEqual(3.46.declinationText, "3.5° E")
    }

    func testMagneticToTrueQiblaForToronto() {
        // A phone reading magnetic 65° in Toronto points about 55° true: almost exactly at the Qibla.
        let lat = 43.6532, lon = -79.3832
        let declination = MagneticModel.shared.declination(latitude: lat, longitude: lon, decimalYear: 2026.8)
        let qibla = PrayerCalculator.qiblaBearing(latitude: lat, longitude: lon)
        XCTAssertTrue(QiblaGuidance(qiblaBearing: qibla, trueHeading: 65 + declination).isAligned)
    }
}
