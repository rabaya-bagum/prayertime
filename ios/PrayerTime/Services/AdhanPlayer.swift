import AVFoundation
import Observation

/// Plays the full adhan in the app: when an adhan notification arrives in the
/// foreground or is opened, and as a preview in Settings.
@MainActor
@Observable
final class AdhanPlayer: NSObject, AVAudioPlayerDelegate {
    static let shared = AdhanPlayer()

    private(set) var playing: AdhanSound?

    @ObservationIgnored private var player: AVAudioPlayer?

    func play(_ sound: AdhanSound) {
        stop()
        guard let url = sound.fullRecording, let player = try? AVAudioPlayer(contentsOf: url) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        player.delegate = self
        player.play()
        self.player = player
        playing = sound
    }

    func stop() {
        player?.stop()
        finish()
    }

    func toggle(_ sound: AdhanSound) {
        if playing == sound { stop() } else { play(sound) }
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
