import SwiftUI

struct AboutView: View {
    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "?") (\(info?["CFBundleVersion"] as? String ?? "?"))"
    }

    var body: some View {
        Form {
            Section {
                Text("Everything is calculated on your phone. Prayer Time has no account, no ads and no analytics, and it never sends your location anywhere.")
            } header: {
                Text("Privacy")
            }

            Section("Credits") {
                Link(destination: URL(string: "https://www.geonames.org")!) {
                    VStack(alignment: .leading) {
                        Text("City data: GeoNames")
                        Text("Licensed under CC BY 4.0").font(.caption).foregroundStyle(.secondary)
                    }
                }
                ForEach(AdhanSound.all) { sound in
                    Link(destination: sound.source) {
                        VStack(alignment: .leading) {
                            Text("Adhan: \(sound.reciter)")
                            Text("\(sound.credit), \(sound.license). Trimmed and normalized.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Link(destination: URL(string: "https://github.com/batoulapps/adhan-swift")!) {
                    VStack(alignment: .leading) {
                        Text("Prayer calculation: Adhan")
                        Text("Batoul Apps, MIT License").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                LabeledContent("Version", value: version)
            }
        }
        .navigationTitle("About")
    }
}
