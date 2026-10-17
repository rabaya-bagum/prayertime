import Foundation

/// One row of the bundled GeoNames extract (see tools/gen_cities.py).
struct City: Hashable, Sendable {
    let name: String
    let region: String
    let country: String
    let countryCode: String
    let latitude: Double
    let longitude: Double
    let timeZoneId: String
    let population: Int
    /// GeoNames' ASCII spelling, so "tromso" finds Tromsø; empty when the same as `name`.
    let asciiName: String

    /// "Ontario, Canada": enough to tell London, England from London, Ontario.
    var subtitle: String {
        region.isEmpty || region == name ? country : "\(region), \(country)"
    }
}

/// Offline city search and nearest-city lookup. The whole file is ~34k rows, so a
/// linear scan is fast enough and keeps the format trivial for Android to share.
final class CityDatabase: Sendable {
    static let shared = CityDatabase(url: Bundle.main.url(forResource: "cities", withExtension: "tsv")!)

    let cities: [City]
    /// Folded name, plus the folded ASCII name after a tab when it differs.
    private let searchKeys: [String]

    init(url: URL) {
        let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
        let knownZones = Set(TimeZone.knownTimeZoneIdentifiers)
        var cities: [City] = []
        for line in text.split(separator: "\n") {
            let f = line.split(separator: "\t", omittingEmptySubsequences: false)
            guard f.count == 9, let lat = Double(f[4]), let lon = Double(f[5]) else { continue }
            let zone = String(f[6])
            // Place requires a zone the OS knows; GeoNames occasionally runs ahead of tzdata.
            guard knownZones.contains(zone) || TimeZone(identifier: zone) != nil else { continue }
            cities.append(City(name: String(f[0]), region: String(f[1]), country: String(f[2]),
                               countryCode: String(f[3]), latitude: lat, longitude: lon,
                               timeZoneId: zone, population: Int(f[7]) ?? 0, asciiName: String(f[8])))
        }
        self.cities = cities
        self.searchKeys = cities.map { $0.asciiName.isEmpty ? Self.fold($0.name) : Self.fold($0.name) + "\t" + Self.fold($0.asciiName) }
    }

    /// Cities whose name starts with the query, then those containing it, most
    /// populous first. Text after a comma narrows by region, country or code:
    /// "london, on" or "london ca".
    func search(_ query: String, limit: Int = 50) -> [City] {
        let parts = query.split(separator: ",", maxSplits: 1).map { Self.fold(String($0)) }
        guard let name = parts.first, !name.isEmpty else { return [] }
        let qualifier = parts.count > 1 ? parts[1] : ""

        var prefix: [City] = [], contains: [City] = []
        for (i, key) in searchKeys.enumerated() where prefix.count < limit {
            guard let range = key.range(of: name) else { continue }
            let city = cities[i]
            if !qualifier.isEmpty && !Self.matches(city, qualifier) { continue }
            let atWordStart = range.lowerBound == key.startIndex || key[key.index(before: range.lowerBound)] == "\t"
            if atWordStart { prefix.append(city) } else { contains.append(city) }
        }
        return Array((prefix + contains).prefix(limit))
    }

    func nearest(latitude: Double, longitude: Double) -> City? {
        cities.min { distance($0, latitude, longitude) < distance($1, latitude, longitude) }
    }

    /// The largest city in a zone, used as a starting place before the user picks one.
    func largest(inTimeZone zoneId: String) -> City? {
        cities.first { $0.timeZoneId == zoneId }
    }

    private static func matches(_ city: City, _ qualifier: String) -> Bool {
        fold(city.countryCode) == qualifier || fold(city.region).hasPrefix(qualifier) || fold(city.country).hasPrefix(qualifier)
    }

    private static func fold(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespaces)
    }

    /// Squared equirectangular distance: only used for ranking, and accurate enough at city scale.
    private func distance(_ city: City, _ lat: Double, _ lon: Double) -> Double {
        var dLon = abs(city.longitude - lon)
        if dLon > 180 { dLon = 360 - dLon }
        let x = dLon * cos((city.latitude + lat) / 2 * .pi / 180)
        let y = city.latitude - lat
        return x * x + y * y
    }
}
