import PrayerCore
import SwiftUI

struct QiblaView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @State private var heading = HeadingProvider()
    @State private var aligned = false
    /// Unwrapped heading for the dial, so 359° → 1° turns 2°, not back round 358°.
    @State private var dialHeading: Double?

    private var place: Place { model.location.place }
    private var qibla: Double { PrayerCalculator.qiblaBearing(latitude: place.latitude, longitude: place.longitude) }
    private var declination: Double {
        MagneticModel.shared.declination(latitude: place.latitude, longitude: place.longitude, at: Date())
    }
    private var trueHeading: Double? {
        heading.magneticHeading.map { ($0 + declination + 360).truncatingRemainder(dividingBy: 360) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    if heading.isAvailable {
                        live
                    } else {
                        noCompass
                    }
                    details
                }
                .padding()
            }
            .navigationTitle("Qibla")
            .onAppear { heading.start() }
            .onDisappear { heading.stop() }
            .onChange(of: scenePhase) { _, phase in
                phase == .active ? heading.start() : heading.stop()
            }
            .onChange(of: trueHeading) { _, value in update(value) }
            .sensoryFeedback(.success, trigger: aligned) { _, now in now }
        }
    }

    // MARK: Live compass

    private var live: some View {
        let guidance = trueHeading.map { QiblaGuidance(qiblaBearing: qibla, trueHeading: $0, wasAligned: aligned) }
        let quality = HeadingQuality(accuracy: heading.accuracy)
        return VStack(spacing: 16) {
            CompassDial(heading: dialHeading ?? 0, qibla: qibla, aligned: guidance?.isAligned ?? false)
                .opacity(guidance == nil ? 0.4 : 1)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Qibla compass")
                .accessibilityValue(guidance?.instruction ?? "Calibrating")

            Text(guidance?.instruction ?? "Calibrating…")
                .font(.title2.bold())
                .foregroundStyle(guidance?.isAligned == true ? Color.accentColor : .primary)
                .contentTransition(.numericText())
                .accessibilityHidden(true)

            if quality.needsCalibration {
                Label("Compass accuracy is low. Move away from metal, magnets and electronics, then wave your phone in a figure-eight.",
                      systemImage: "exclamationmark.triangle")
                    .font(.footnote)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.yellow.opacity(0.2), in: .rect(cornerRadius: 12))
            } else {
                Text("Hold your phone flat, top edge pointing ahead.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: No magnetometer (simulator, some iPads)

    private var noCompass: some View {
        VStack(spacing: 16) {
            CompassDial(heading: 0, qibla: qibla, aligned: false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Qibla direction")
                .accessibilityValue("\(Int(qibla.rounded())) degrees clockwise from true north")
            Text("This device has no compass. The dial shows true north at the top; face \(Int(qibla.rounded()))° clockwise from north.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: Details

    private var details: some View {
        VStack(spacing: 0) {
            row("Location", "\(place.name), \(model.location.subtitle)")
            Divider()
            row("Qibla", String(format: "%.1f° from true north", qibla))
            Divider()
            row("Magnetic declination", declination.declinationText)
            if heading.isAvailable {
                Divider()
                row("Compass accuracy", HeadingQuality(accuracy: heading.accuracy).summary)
            }
            if !MagneticModel.shared.validYears.contains(MagneticModel.decimalYear(Date())) {
                Divider()
                row("Magnetic model", "\(MagneticModel.shared.name) has expired; update the app")
            }
        }
        .font(.footnote)
        .padding(.horizontal)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
    }

    private func row(_ title: String, _ value: String) -> some View {
        LabeledContent(title, value: value)
            .padding(.vertical, 10)
            .accessibilityElement(children: .combine)
    }

    // MARK: Updates

    private func update(_ value: Double?) {
        guard let value else { return }
        let current = dialHeading ?? value
        withAnimation(.easeOut(duration: 0.2)) {
            dialHeading = current + QiblaGuidance.normalized(value - current)
        }
        let nowAligned = QiblaGuidance(qiblaBearing: qibla, trueHeading: value, wasAligned: aligned).isAligned
        if nowAligned != aligned {
            aligned = nowAligned
            if nowAligned {
                // Audio feedback for VoiceOver users, alongside the haptic.
                AccessibilityNotification.Announcement("Facing the Qibla").post()
            }
        }
    }
}

/// The dial turns with the phone so north stays north; the Kaaba marker sits at the
/// Qibla bearing on the ring and the needle points to it. Never mirrored for RTL.
private struct CompassDial: View {
    let heading: Double
    let qibla: Double
    let aligned: Bool

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let radius = size / 2
            ZStack {
                Circle().fill(Color(.secondarySystemGroupedBackground))
                Circle().strokeBorder(aligned ? Color.accentColor : Color(.separator), lineWidth: aligned ? 4 : 1)

                ZStack {
                    Canvas { context, canvasSize in
                        let center = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
                        for degree in stride(from: 0, to: 360, by: 5) {
                            let major = degree % 30 == 0
                            let angle = Angle.degrees(Double(degree) - 90).radians
                            let outer = radius - 8, inner = outer - (major ? 14 : 7)
                            var path = Path()
                            path.move(to: CGPoint(x: center.x + inner * cos(angle), y: center.y + inner * sin(angle)))
                            path.addLine(to: CGPoint(x: center.x + outer * cos(angle), y: center.y + outer * sin(angle)))
                            context.stroke(path, with: .color(.secondary), lineWidth: major ? 2 : 1)
                        }
                    }
                    ForEach([("N", 0.0), ("E", 90.0), ("S", 180.0), ("W", 270.0)], id: \.0) { label, angle in
                        Text(label)
                            .font(.headline)
                            .foregroundStyle(label == "N" ? .red : .primary)
                            .rotationEffect(.degrees(heading - angle)) // keep letters upright
                            .offset(y: -(radius - 40))
                            .rotationEffect(.degrees(angle))
                    }
                    KaabaMarker()
                        .rotationEffect(.degrees(heading - qibla))
                        .offset(y: -(radius - 40))
                        .rotationEffect(.degrees(qibla))
                }
                .rotationEffect(.degrees(-heading))

                // Needle toward the Qibla.
                Capsule()
                    .fill(aligned ? Color.accentColor : Color.primary.opacity(0.7))
                    .frame(width: aligned ? 6 : 4, height: radius - 70)
                    .offset(y: -(radius - 70) / 2)
                    .rotationEffect(.degrees(qibla - heading))
                Circle().fill(Color.primary).frame(width: 12, height: 12)

                // Where the phone points.
                Triangle()
                    .fill(aligned ? Color.accentColor : Color.primary)
                    .frame(width: 18, height: 14)
                    .offset(y: -radius - 10)
            }
            .frame(width: size, height: size)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: 340)
        .padding(.top, 16)
        .environment(\.layoutDirection, .leftToRight)
    }
}

private struct KaabaMarker: View {
    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 3).fill(.black)
            Rectangle().fill(Color(red: 0.95, green: 0.77, blue: 0.43)).frame(height: 3).padding(.top, 5)
        }
        .frame(width: 22, height: 22)
        .overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(.white.opacity(0.6), lineWidth: 1))
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: rect.midX, y: rect.maxY))
            p.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            p.closeSubpath()
        }
    }
}

#Preview {
    QiblaView().environment(AppModel())
}
