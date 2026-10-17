import PrayerCore
import SwiftUI

struct TodayView: View {
    @Environment(AppModel.self) private var model
    @State private var showingLocations = false
    @State private var showingSettings = false

    var body: some View {
        NavigationStack {
            // Ticks every second for the countdown; recomputing a day is cheap and offline.
            TimelineView(.periodic(from: .now, by: 1)) { context in
                TodayContent(location: model.location, settings: model.settings, now: context.date)
            }
            .safeAreaInset(edge: .bottom) {
                if model.notificationStatus == .denied {
                    Text("Notifications are off, so no adhan alerts will sound. Turn them on in the Settings app.")
                        .font(.footnote)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(.yellow.opacity(0.25))
                }
            }
            .navigationTitle(model.location.place.name)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showingLocations = true } label: {
                        Label("Change location", systemImage: model.location.isCurrentLocation ? "location.fill" : "mappin.and.ellipse")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingSettings = true } label: {
                        Label("Settings", systemImage: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showingLocations) { LocationPickerView() }
            .sheet(isPresented: $showingSettings) { SettingsView() }
        }
    }
}

private struct TodayContent: View {
    let location: SavedLocation
    let settings: PrayerSettings
    let now: Date

    var body: some View {
        let place = location.place
        let schedule = PrayerCalculator.day(at: place, on: CivilDate(now, in: place.timeZone), settings: settings)
        let next = PrayerCalculator.upcoming(at: place, settings: settings, from: now, count: 1).first

        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(location.subtitle).foregroundStyle(.secondary)
                    Text(now, format: .dateTime.weekday(.wide).day().month(.wide).year())
                    Text(schedule.hijri.formatted).foregroundStyle(.secondary)
                    if schedule.hijri.isRamadan {
                        Text("Imsak \(schedule.imsak.formatted(Self.timeStyle(place)))")
                            .foregroundStyle(.secondary)
                    }
                }
                .font(.subheadline)
                .accessibilityElement(children: .combine)
            }

            if let next {
                Section("Next") {
                    HStack(alignment: .firstTextBaseline) {
                        Text(next.prayer.displayName).font(.title2.bold())
                        Spacer()
                        Text(countdown(to: next.time)).font(.title2.monospacedDigit())
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(next.prayer.displayName) at \(next.time.formatted(Self.timeStyle(place))), in \(spokenCountdown(to: next.time))")
                }
            }

            Section("Today") {
                ForEach(Prayer.allCases, id: \.self) { prayer in
                    let isNext = next?.prayer == prayer && next?.date == schedule.date
                    HStack {
                        Text(prayer.displayName)
                        Spacer()
                        Text(schedule[prayer], format: Self.timeStyle(place)).monospacedDigit()
                    }
                    .fontWeight(isNext ? .semibold : .regular)
                    .foregroundStyle(prayer == .sunrise ? .secondary : .primary)
                    .listRowBackground(isNext ? Color.accentColor.opacity(0.15) : nil)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(isNext ? .isSelected : [])
                }
            }

            Section {
                LabeledContent("Method", value: schedule.method.displayName)
                if settings.madhab == .hanafi { LabeledContent("Asr", value: "Hanafi") }
                LabeledContent("High latitude", value: schedule.adjustment.displayName)
                LabeledContent("Qibla", value: String(format: "%.0f° from true north",
                    PrayerCalculator.qiblaBearing(latitude: place.latitude, longitude: place.longitude)))
            }
            .font(.footnote)
        }
        // Text(_:format:) takes its zone from the environment, not the style, so every
        // time on screen must be pinned to the place's zone here, not the device's.
        .environment(\.timeZone, place.timeZone)
    }

    static func timeStyle(_ place: Place) -> Date.FormatStyle {
        Date.FormatStyle(date: .omitted, time: .shortened, timeZone: place.timeZone)
    }

    private func countdown(to time: Date) -> String {
        let seconds = max(0, Int(time.timeIntervalSince(now)))
        return String(format: "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    private func spokenCountdown(to time: Date) -> String {
        let minutes = max(0, Int(time.timeIntervalSince(now)) / 60)
        return Duration.seconds(minutes * 60).formatted(.units(allowed: [.hours, .minutes], width: .wide))
    }
}

#Preview {
    TodayView().environment(AppModel())
}
