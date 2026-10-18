import Foundation
import PrayerCore

/// One bundled recording and whom to credit for it.
struct AdhanRecording: Hashable, Sendable {
    let reciter: String
    let credit: String
    let license: String
    let source: URL
}

/// An adhan voice bundled with the app, built by tools/make_adhan_audio.sh.
/// Sources and licenses are in docs/AUDIO_LICENSES.md.
struct AdhanSound: Identifiable, Hashable, Sendable {
    /// The `PrayerAlert.sound` id; files are `<id>_clip.caf`, `<id>_adhan.m4a`
    /// and, when there's a Fajr recording, `<id>_fajr_adhan.m4a`.
    let id: String
    let regular: AdhanRecording
    /// Fajr adds "As-salatu khayrun min an-nawm" twice, so the regular recording
    /// can't stand in for it. It may be another muezzin's when the reciter has none.
    let fajr: AdhanRecording?

    static let all = [
        AdhanSound(
            id: "azeez",
            regular: AdhanRecording(
                reciter: "Aaqib Azeez", credit: "Recording by Atcovi, Wikimedia Commons", license: "CC BY-SA 4.0",
                source: URL(string: "https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3")!),
            fajr: AdhanRecording(
                reciter: "Besim Azemi", credit: "Islamic Center Malmö, via Wikimedia Commons", license: "CC BY 3.0",
                source: URL(string: "https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm")!)
        ),
    ]

    /// Falls back to the first voice for ids from older settings.
    static func named(_ id: String) -> AdhanSound { all.first { $0.id == id } ?? all[0] }

    var recordings: [AdhanRecording] { [regular] + (fajr.map { [$0] } ?? []) }

    /// Who is heard when the full adhan for `prayer` plays.
    func recording(for prayer: Prayer?) -> AdhanRecording {
        prayer == .fajr ? fajr ?? regular : regular
    }

    /// What to play in the app for `prayer`. Without a Fajr recording, Fajr gets
    /// the opening takbir only, which is the same in every adhan.
    func file(for prayer: Prayer?, in bundle: Bundle = .main) -> URL? {
        if prayer == .fajr {
            return fajr != nil ? bundle.url(forResource: "\(id)_fajr_adhan", withExtension: "m4a") : clip(in: bundle)
        }
        return bundle.url(forResource: "\(id)_adhan", withExtension: "m4a")
    }

    func clip(in bundle: Bundle = .main) -> URL? { bundle.url(forResource: "\(id)_clip", withExtension: "caf") }

    /// Opening an adhan notification plays the full recording, but only near the
    /// prayer: tapping an old notification hours later shouldn't start an adhan.
    static func shouldPlayFull(kind: String, firedAt: Date, now: Date = Date()) -> Bool {
        kind == "adhan" && now.timeIntervalSince(firedAt) < 10 * 60
    }
}
