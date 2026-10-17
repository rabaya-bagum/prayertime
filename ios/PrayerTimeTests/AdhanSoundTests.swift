import AVFoundation
import PrayerCore
import XCTest
@testable import PrayerTime

final class AdhanSoundTests: XCTestCase {
    func testEveryRecordingIsBundledAndItsClipFitsIOSLimit() throws {
        for sound in AdhanSound.all {
            let clip = try XCTUnwrap(sound.clip, "\(sound.id)_clip.caf missing")
            let file = try AVAudioFile(forReading: clip)
            let seconds = Double(file.length) / file.processingFormat.sampleRate
            XCTAssertLessThan(seconds, 30, "iOS plays the default sound for clips of 30 s or more")
            XCTAssertNotNil(sound.fullRecording, "\(sound.id)_adhan.m4a missing")
        }
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
