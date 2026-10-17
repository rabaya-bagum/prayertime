import SwiftUI

struct LocationPickerView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [City] = []
    @State private var locating = false
    @State private var error: String?
    @State private var locationProvider = LocationProvider()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        Task { await useCurrentLocation() }
                    } label: {
                        HStack {
                            Label("Use current location", systemImage: "location")
                            if locating { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(locating)
                } footer: {
                    if let error { Text(error).foregroundStyle(.red) }
                    else { Text("Approximate location only. It never leaves your phone.") }
                }

                if !results.isEmpty {
                    Section("Cities") {
                        ForEach(results, id: \.self) { city in
                            Button { choose(SavedLocation(city: city)) } label: {
                                VStack(alignment: .leading) {
                                    Text(city.name).foregroundStyle(.primary)
                                    Text(city.subtitle).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                } else if !query.isEmpty {
                    ContentUnavailableView.search(text: query)
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search cities")
            .onChange(of: query) { _, text in results = CityDatabase.shared.search(text) }
            .navigationTitle("Location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }

    private func useCurrentLocation() async {
        locating = true
        error = nil
        defer { locating = false }
        do {
            let fix = try await locationProvider.currentLocation()
            let lat = fix.coordinate.latitude, lon = fix.coordinate.longitude
            guard let city = CityDatabase.shared.nearest(latitude: lat, longitude: lon) else { return }
            choose(SavedLocation(fixLatitude: lat, longitude: lon, nearest: city))
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func choose(_ location: SavedLocation) {
        model.setLocation(location)
        dismiss()
    }
}
