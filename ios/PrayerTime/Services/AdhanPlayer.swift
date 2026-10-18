import AVFoundation
import Observation
import PrayerCore

/// Plays the full adhan in the app: when an adhan notification arrives in the
/// foreground or is opened, and as a preview in Settings.
@MainActor
@Observable
final class AdhanPlayer: NSObject, AVAudioPlayerDelegate {
    struct Playing: Equatable {
        let sound: AdhanSound
        let prayer: Prayer?
        var recording: AdhanRecording { sound.recording(for: prayer) }
    }

    static let shared = AdhanPlayer()

    private(set) var playing: Playing?

    @ObservationIgnored private var player: AVAudioPlayer?

    func play(_ sound: AdhanSound, for prayer: Prayer?) {
        stop()
        guard let url = sound.file(for: prayer), let player = try? AVAudioPlayer(contentsOf: url) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        player.delegate = self
        player.play()
        self.player = player
        playing = Playing(sound: sound, prayer: prayer)
    }

    func stop() {
        player?.stop()
        finish()
    }

    func toggle(_ sound: AdhanSound, for prayer: Prayer?) {
        if playing == Playing(sound: sound, prayer: prayer) { stop() } else { play(sound, for: prayer) }
    }

    func isPlaying(_ sound: AdhanSound, for prayer: Prayer?) -> Bool {
        playing == Playing(sound: sound, prayer: prayer)
    }

    private func finish() {
        player = nil
        playing = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        MainActor.assumeIsolated { finish() }
    }
}
