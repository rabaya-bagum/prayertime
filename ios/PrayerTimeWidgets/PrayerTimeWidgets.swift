import PrayerCore
import SwiftUI
import WidgetKit

@main
struct PrayerTimeWidgets: WidgetBundle {
    var body: some Widget {
        NextPrayerWidget()
        TodayWidget()
    }
}

/// The next prayer with a live countdown: home screen small, and every lock screen family.
struct NextPrayerWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NextPrayer", provider: PrayerTimelineProvider()) { entry in
            NextPrayerView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Next prayer")
        .description("The next prayer and a countdown to it.")
        .supportedFamilies([.systemSmall, .accessoryInline, .accessoryCircular, .accessoryRectangular])
    }
}

/// The day's six times with the next one highlighted.
struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "Today", provider: PrayerTimelineProvider()) { entry in
            TodayView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Today's prayers")
        .description("All of today's prayer times, with the next one highlighted.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: Timeline

struct PrayerEntry: TimelineEntry {
    let date: Date
    /// Nil until the app has been opened once and shared its location.
    let content: Content?

    struct Content {
        let place: Place
        let subtitle: String
        let prayer: PrayerWidgetEntry
    }
}

struct PrayerTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> PrayerEntry {
        entries(from: Date(), configuration: Self.sample).first!
    }

    func getSnapshot(in context: Context, completion: @escaping (PrayerEntry) -> Void) {
        let configuration = SharedConfiguration.load() ?? Self.sample
        completion(entries(from: Date(), configuration: configuration).first!)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PrayerEntry>) -> Void) {
        guard let configuration = SharedConfiguration.load() else {
            // The app writes the configuration on launch; check back in an hour.
            completion(Timeline(entries: [PrayerEntry(date: Date(), content: nil)], policy: .after(Date().addingTimeInterval(3600))))
            return
        }
        let entries = entries(from: Date(), configuration: configuration)
        completion(Timeline(entries: entries, policy: .after(entries.last?.date ?? Date().addingTimeInterval(3600))))
    }

    private func entries(from now: Date, configuration: SharedConfiguration) -> [PrayerEntry] {
        WidgetTimeline.entries(configuration, from: now).map {
            PrayerEntry(date: $0.date, content: .init(place: configuration.place, subtitle: configuration.subtitle, prayer: $0))
        }
    }

    /// Shown in the widget gallery before the app has shared a location.
    private static let sample = SharedConfiguration(
        place: Place(name: "Makkah", latitude: 21.4225, longitude: 39.8262, timeZoneId: "Asia/Riyadh"),
        subtitle: "Makkah Province, Saudi Arabia",
        settings: PrayerSettings(method: .ummAlQura)
    )
}

// MARK: Views

private let accent = Color(red: 0.055, green: 0.420, blue: 0.384)

struct NextPrayerView: View {
    let entry: PrayerEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let content = entry.content {
            let next = content.prayer.next
            let name = next.prayer.displayName
            Group {
                switch family {
                case .accessoryInline:
                    Text("\(name) \(next.time, style: .time)")
                case .accessoryCircular:
                    VStack(spacing: 0) {
                        Text(name).font(.caption2).widgetAccentable()
                        Text(next.time, style: .time).font(.caption2.monospacedDigit()).minimumScaleFactor(0.6)
                    }
                case .accessoryRectangular:
                    VStack(alignment: .leading, spacing: 0) {
                        Text(name).font(.headline).widgetAccentable()
                        Text(next.time, style: .time)
                        Text(timerInterval: entry.date...next.time, countsDown: true).monospacedDigit()
                    }
                default:
                    VStack(alignment: .leading, spacing: 4) {
                        Text(content.place.name).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(name).font(.title2.bold()).foregroundStyle(accent)
                        Text(next.time, style: .time).font(.title3.monospacedDigit())
                        Text(timerInterval: entry.date...next.time, countsDown: true)
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            // Times show on the place's clock, which may differ from the phone's zone.
            .environment(\.timeZone, content.place.timeZone)
            .accessibilityElement(children: .combine)
        } else {
            Text("Open Prayer Time to choose your city.")
                .font(.caption)
                .multilineTextAlignment(.center)
        }
    }
}

struct TodayView: View {
    let entry: PrayerEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let content = entry.content {
            let prayer = content.prayer
            let isNextToday = prayer.next.date == prayer.day
            VStack(alignment: .leading, spacing: family == .systemLarge ? 10 : 2) {
                HStack(alignment: .firstTextBaseline) {
                    Text(content.place.name).font(.headline).lineLimit(1)
                    Spacer()
                    Text(timerInterval: entry.date...prayer.next.time, countsDown: true)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: 70, alignment: .trailing)
                }
                if family == .systemLarge {
                    Text(content.subtitle).font(.caption).foregroundStyle(.secondary)
                }
                ForEach(Prayer.allCases, id: \.self) { p in
                    let isNext = isNextToday && prayer.next.prayer == p
                    HStack {
                        Text(p.displayName)
                        Spacer()
                        Text(prayer.times[p]!, style: .time).monospacedDigit()
                    }
                    .font(family == .systemLarge ? .body : .caption)
                    .fontWeight(isNext ? .semibold : .regular)
                    .foregroundStyle(isNext ? accent : p == .sunrise ? .secondary : .primary)
                }
            }
            .environment(\.timeZone, content.place.timeZone)
        } else {
            Text("Open Prayer Time to choose your city.")
                .font(.caption)
        }
    }
}

extension Prayer {
    /// Same names as the app's DisplayNames.swift.
    var displayName: String {
        switch self {
        case .fajr: return "Fajr"
        case .sunrise: return "Sunrise"
        case .dhuhr: return "Dhuhr"
        case .asr: return "Asr"
        case .maghrib: return "Maghrib"
        case .isha: return "Isha"
        }
    }
}
