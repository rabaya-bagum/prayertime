import PrayerCore
import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            Form {
                calculation
                adjustments
                notifications
                Section {
                    NavigationLink("About") { AboutView() }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }

    // MARK: Calculation

    private var calculation: some View {
        Section {
            Picker("Method", selection: setting(\.method, apply: { s, method in
                // Custom needs angles before the method switches (PrayerSettings precondition).
                if method == .custom && s.customAngles == nil {
                    s.customAngles = CustomAngles(fajrAngle: 18, ishaAngle: 17)
                }
                s.method = method
            })) {
                ForEach(PrayerCore.Method.allCases, id: \.self) { Text($0.displayName).tag($0) }
            }

            if model.settings.method == .custom, let angles = model.settings.customAngles {
                Stepper(value: customAngle(\.fajrAngle), in: 10...25, step: 0.5) {
                    LabeledContent("Fajr angle", value: "\(angles.fajrAngle.formatted())°")
                }
                Stepper(value: customInterval, in: 0...150, step: 5) {
                    LabeledContent("Isha interval", value: angles.ishaIntervalMinutes == 0 ? "Use angle" : "\(angles.ishaIntervalMinutes) min after Maghrib")
                }
                if angles.ishaIntervalMinutes == 0 {
                    Stepper(value: customAngle(\.ishaAngle), in: 10...25, step: 0.5) {
                        LabeledContent("Isha angle", value: "\(angles.ishaAngle.formatted())°")
                    }
                }
            }

            Picker("Asr", selection: setting(\.madhab)) {
                Text("Standard (Shafi'i, Maliki, Hanbali)").tag(Madhab.shafi)
                Text("Hanafi").tag(Madhab.hanafi)
            }

            Picker("High latitude", selection: setting(\.highLatitudeRule)) {
                ForEach([HighLatitudeRule.auto, .middleOfTheNight, .seventhOfTheNight, .twilightAngle], id: \.self) {
                    Text($0.displayName).tag($0)
                }
            }

            if model.settings.method == .moonsightingCommittee {
                Picker("Isha twilight", selection: setting(\.shafaq)) {
                    ForEach([Shafaq.general, .ahmer, .abyad], id: \.self) { Text($0.displayName).tag($0) }
                }
            }

            if model.settingsCustomized {
                Button("Use defaults for \(model.location.subtitle)") { model.resetSettings() }
            }
        } header: {
            Text("Calculation")
        } footer: {
            Text("Mosques differ, sometimes by 40 minutes or more. Pick the method your local mosque follows, then fine-tune each prayer below.")
        }
    }

    // MARK: Adjustments

    private var adjustments: some View {
        Section("Adjustments") {
            ForEach(Prayer.allCases, id: \.self) { prayer in
                let minutes = model.settings.offsetMinutes[prayer] ?? 0
                Stepper(value: offset(prayer), in: -60...60) {
                    LabeledContent(prayer.displayName, value: minutes == 0 ? "No change" : "\(minutes > 0 ? "+" : "")\(minutes) min")
                }
            }
            Stepper(value: setting(\.hijriOffsetDays), in: -2...2) {
                LabeledContent("Hijri date", value: model.settings.hijriOffsetDays == 0
                    ? "Umm al-Qura" : "\(model.settings.hijriOffsetDays > 0 ? "+" : "")\(model.settings.hijriOffsetDays) day")
            }
            Stepper(value: setting(\.imsakMinutesBeforeFajr), in: 0...30, step: 5) {
                LabeledContent("Imsak", value: "\(model.settings.imsakMinutesBeforeFajr) min before Fajr")
            }
        }
    }

    // MARK: Notifications

    private var notifications: some View {
        Section {
            if model.notificationStatus == .denied {
                Button("Turn on notifications in Settings") {
                    openURL(URL(string: UIApplication.openSettingsURLString)!)
                }
            }
            ForEach(Prayer.allCases, id: \.self) { prayer in
                Picker(prayer.displayName, selection: alertMode(prayer)) {
                    ForEach(AlertMode.allCases, id: \.self) { Text($0.displayName).tag($0) }
                }
            }
            Picker("Reminder before", selection: Binding(
                get: { model.alerts.reminderMinutes },
                set: { value in model.updateAlerts { $0.reminderMinutes = value } }
            )) {
                ForEach(AlertPreferences.reminderChoices, id: \.self) { Text($0 == 0 ? "None" : "\($0) min").tag($0) }
            }
        } header: {
            Text("Adhan alerts")
        } footer: {
            Text("iOS keeps at most 64 alerts waiting, about a week and a half of prayers. Open the app now and then so it can schedule more. \(model.pendingAlertCount) scheduled now.")
        }
    }

    // MARK: Bindings

    private func setting<T>(_ keyPath: WritableKeyPath<PrayerSettings, T>,
                            apply: ((inout PrayerSettings, T) -> Void)? = nil) -> Binding<T> {
        Binding(
            get: { model.settings[keyPath: keyPath] },
            set: { value in model.updateSettings { s in
                if let apply { apply(&s, value) } else { s[keyPath: keyPath] = value }
            } }
        )
    }

    private func offset(_ prayer: Prayer) -> Binding<Int> {
        Binding(
            get: { model.settings.offsetMinutes[prayer] ?? 0 },
            set: { value in model.updateSettings { $0.offsetMinutes[prayer] = value == 0 ? nil : value } }
        )
    }

    private func customAngle(_ keyPath: WritableKeyPath<CustomAngles, Double>) -> Binding<Double> {
        Binding(
            get: { model.settings.customAngles?[keyPath: keyPath] ?? 0 },
            set: { value in model.updateSettings { $0.customAngles?[keyPath: keyPath] = value } }
        )
    }

    private var customInterval: Binding<Int> {
        Binding(
            get: { model.settings.customAngles?.ishaIntervalMinutes ?? 0 },
            set: { value in model.updateSettings { $0.customAngles?.ishaIntervalMinutes = value } }
        )
    }

    private func alertMode(_ prayer: Prayer) -> Binding<AlertMode> {
        Binding(
            get: { model.alerts.mode(for: prayer) },
            set: { value in model.updateAlerts { $0.modes[prayer] = value } }
        )
    }
}
