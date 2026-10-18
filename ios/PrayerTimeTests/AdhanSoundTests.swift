import AVFoundation
import PrayerCore
import XCTest
@testable import PrayerTime

final class AdhanSoundTests: XCTestCase {
    func testEveryRecordingIsBundledAndItsClipFitsIOSLimit() throws {
        for sound in AdhanSound.all {
            let clip = try XCTUnwrap(sound.clip(), "\(sound.id)_clip.caf missing")
            let file = try AVAudioFile(forReading: clip)
            let seconds = Double(file.length) / file.processingFormat.sampleRate
            XCTAssertLessThan(seconds, 30, "iOS plays the default sound for clips of 30 s or more")
            XCTAssertNotNil(sound.file(for: .dhuhr), "\(sound.id)_adhan.m4a missing")
            if sound.fajr != nil {
                XCTAssertNotNil(sound.file(for: .fajr), "\(sound.id)_fajr_adhan.m4a missing")
            }
        }
    }

    /// Fajr adds "As-salatu khayrun min an-nawm"; the regular recording lacks it.
    func testFajrNeverPlaysTheRegularRecording() throws {
        for sound in AdhanSound.all {
            let regular = try XCTUnwrap(sound.file(for: .dhuhr))
            XCTAssertNotEqual(sound.file(for: .fajr), regular, sound.id)
            for prayer in Prayer.allCases where prayer != .fajr {
                XCTAssertEqual(sound.file(for: prayer), regular)
            }
        }

        let withoutFajr = AdhanSound(id: "azeez", regular: AdhanSound.all[0].regular, fajr: nil)
        XCTAssertEqual(withoutFajr.file(for: .fajr), withoutFajr.clip(), "no Fajr recording: the takbir clip only")
        XCTAssertEqual(withoutFajr.recording(for: .fajr), withoutFajr.regular)
    }

    func testFajrIsCreditedToItsOwnReciter() {
        let sound = AdhanSound.named("azeez")
        XCTAssertEqual(sound.recording(for: .fajr).reciter, "Besim Azemi")
        XCTAssertEqual(sound.recording(for: .maghrib).reciter, "Aaqib Azeez")
        XCTAssertEqual(sound.recordings.count, 2)
    }

    func testDefaultSoundIsBundled() {
        XCTAssertEqual(AdhanSound.named(PrayerAlert.defaultSound).id, PrayerAlert.defaultSound)
        XCTAssertEqual(AdhanSound.named("unknown"), AdhanSound.all[0])
    }

    func testOpeningANotificationPlaysTheFullAdhanOnlyNearThePrayer() {
        let fired = Date(timeIntervalSince1970: 1_778_923_200)
        XCTAssertTrue(AdhanSound.shouldPlayFull(kind: "adhan", firedAt: fired, now: fired.addingTimeInterval(60)))
        XCTAssertFalse(AdhanSound.shouldPlayFull(kind: "adhan", firedAt: fired, now: fired.addingTimeInterval(3600)))
        XCTAssertFalse(AdhanSound.shouldPlayFull(kind: "reminder", firedAt: fired, now: fired))
    }
}
