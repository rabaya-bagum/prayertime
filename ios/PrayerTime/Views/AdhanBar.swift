import SwiftUI

/// A stop control along the bottom while the full adhan plays.
struct AdhanBar: ViewModifier {
    private var player: AdhanPlayer { .shared }

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom) {
            if let playing = player.playing {
                HStack {
                    Image(systemName: "speaker.wave.2.fill").foregroundStyle(.tint)
                    VStack(alignment: .leading) {
                        Text(playing.prayer == .fajr ? "Fajr adhan" : "Adhan").font(.headline)
                        Text(playing.recording.reciter).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Stop", systemImage: "stop.fill") { player.stop() }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("Stop adhan")
                }
                .padding()
                .background(.regularMaterial, in: .rect(cornerRadius: 16))
                .padding(.horizontal)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.default, value: player.playing)
    }
}

extension View {
    func adhanBar() -> some View { modifier(AdhanBar()) }
}
