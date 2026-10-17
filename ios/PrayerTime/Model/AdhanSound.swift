import Foundation

/// An adhan recording bundled with the app, built by tools/make_adhan_audio.sh.
/// Sources and licenses are in docs/AUDIO_LICENSES.md.
struct AdhanSound: Identifiable, Hashable, Sendable {
    /// The `PrayerAlert.sound` id; files are `<id>_clip.caf` and `<id>_adhan.m4a`.
    let id: String
    let reciter: String
    let credit: String
    let license: String
    let source: URL

    static let all = [
        AdhanSound(id: "azeez", reciter: "Aaqib Azeez", credit: "Recording by Atcovi, Wikimedia Commons",
                   license: "CC BY-SA 4.0",
                   source: URL(string: "https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3")!),
    ]

    /// Falls back to the first recording for ids from older settings.
    static func named(_ id: String) -> AdhanSound { all.first { $0.id == id } ?? all[0] }

    /// The full recording, played in the app; the notification itself plays the clip.
    var fullRecording: URL? { Bundle.main.url(forResource: "\(id)_adhan", withExtension: "m4a") }
    var clip: URL? { Bundle.main.url(forResource: "\(id)_clip", withExtension: "caf") }

    /// Opening an adhan notification plays the full recording, but only near the
    /// prayer: tapping an old notification hours later shouldn't start an adhan.
    static func shouldPlayFull(kind: String, firedAt: Date, now: Date = Date()) -> Bool {
        kind == "adhan" && now.timeIntervalSince(firedAt) < 10 * 60
    }
}
