import Foundation

/// The five daily prayers plus sunrise, which ends the Fajr window.
public enum Prayer: String, CaseIterable, Codable, Sendable {
    case fajr, sunrise, dhuhr, asr, maghrib, isha
}

/// Calculation conventions offered to the user. Every preset maps to an Adhan
/// method; `.custom` uses the angles in `PrayerSettings.customAngles`.
///
/// Only methods present in both adhan-swift and adhan-kotlin are listed, so both
/// apps offer the same choices. (Tehran needs a Maghrib angle, which only
/// adhan-swift supports.) Raw values are stored in settings and shared test
/// data, so never rename them.
public enum Method: String, CaseIterable, Codable, Sendable {
    case muslimWorldLeague = "mwl"
    case isna = "isna"
    case moonsightingCommittee = "moonsighting"
    case egyptian = "egyptian"
    case karachi = "karachi"
    case ummAlQura = "ummalqura"
    case dubai = "dubai"
    case kuwait = "kuwait"
    case qatar = "qatar"
    case singapore = "singapore"
    case turkey = "turkey"
    case custom = "custom"

    public var displayName: String {
        switch self {
        case .muslimWorldLeague: return "Muslim World League"
        case .isna: return "ISNA (North America)"
        case .moonsightingCommittee: return "Moonsighting Committee"
        case .egyptian: return "Egyptian General Authority of Survey"
        case .karachi: return "University of Islamic Sciences, Karachi"
        case .ummAlQura: return "Umm al-Qura, Makkah"
        case .dubai: return "Dubai"
        case .kuwait: return "Kuwait"
        case .qatar: return "Qatar"
        case .singapore: return "Singapore (MUIS)"
        case .turkey: return "Diyanet, Turkey"
        case .custom: return "Custom angles"
        }
    }
}

public enum Madhab: String, Codable, Sendable {
    case shafi, hanafi
}

/// `.auto` follows Adhan's recommendation: seventh of the night above 48°, middle of the night below.
public enum HighLatitudeRule: String, Codable, Sendable {
    case auto, middleOfTheNight, seventhOfTheNight, twilightAngle
}

/// Twilight used by the Moonsighting Committee method for Isha. Ignored by other methods.
public enum Shafaq: String, Codable, Sendable {
    case general, ahmer, abyad
}

/// Fajr/Isha angles for `.custom`. A positive `ishaIntervalMinutes` replaces the Isha angle.
public struct CustomAngles: Equatable, Codable, Sendable {
    public var fajrAngle: Double
    public var ishaAngle: Double
    public var ishaIntervalMinutes: Int

    public init(fajrAngle: Double, ishaAngle: Double, ishaIntervalMinutes: Int = 0) {
        precondition((0...30).contains(fajrAngle), "Fajr angle out of range: \(fajrAngle)")
        precondition((0...30).contains(ishaAngle), "Isha angle out of range: \(ishaAngle)")
        precondition((0...240).contains(ishaIntervalMinutes), "Isha interval out of range")
        self.fajrAngle = fajrAngle
        self.ishaAngle = ishaAngle
        self.ishaIntervalMinutes = ishaIntervalMinutes
    }
}

public struct PrayerSettings: Equatable, Codable, Sendable {
    public var method: Method
    public var madhab: Madhab
    public var highLatitudeRule: HighLatitudeRule
    public var shafaq: Shafaq
    public var customAngles: CustomAngles?
    /// User's per-prayer ± minutes, applied on top of the method's own adjustments.
    public var offsetMinutes: [Prayer: Int]
    /// Shifts the Umm al-Qura Hijri date to match local moon sighting.
    public var hijriOffsetDays: Int
    /// Imsak is a convention, not a ruling, so the gap before Fajr is configurable.
    public var imsakMinutesBeforeFajr: Int
    /// Minutes added to Isha during Ramadan. Nil means the method's own rule:
    /// +30 for Umm al-Qura (Isha = Maghrib + 120), 0 otherwise.
    public var ramadanIshaExtensionMinutes: Int?

    public init(
        method: Method,
        madhab: Madhab = .shafi,
        highLatitudeRule: HighLatitudeRule = .auto,
        shafaq: Shafaq = .general,
        customAngles: CustomAngles? = nil,
        offsetMinutes: [Prayer: Int] = [:],
        hijriOffsetDays: Int = 0,
        imsakMinutesBeforeFajr: Int = 10,
        ramadanIshaExtensionMinutes: Int? = nil
    ) {
        precondition(method != .custom || customAngles != nil, "custom method needs customAngles")
        precondition((-2...2).contains(hijriOffsetDays), "Hijri offset must be within ±2 days")
        precondition((0...60).contains(imsakMinutesBeforeFajr), "Imsak gap out of range")
        precondition(offsetMinutes.values.allSatisfy { (-60...60).contains($0) }, "Prayer offsets must be within ±60 minutes")
        self.method = method
        self.madhab = madhab
        self.highLatitudeRule = highLatitudeRule
        self.shafaq = shafaq
        self.customAngles = customAngles
        self.offsetMinutes = offsetMinutes
        self.hijriOffsetDays = hijriOffsetDays
        self.imsakMinutesBeforeFajr = imsakMinutesBeforeFajr
        self.ramadanIshaExtensionMinutes = ramadanIshaExtensionMinutes
    }
}

/// A location the user picked or that GPS resolved to. The IANA zone is stored
/// with it, never a fixed offset, so DST and cities in other zones resolve correctly.
public struct Place: Equatable, Codable, Sendable {
    public var name: String
    public var latitude: Double
    public var longitude: Double
    public var timeZoneId: String

    public var timeZone: TimeZone { TimeZone(identifier: timeZoneId)! }

    public init(name: String, latitude: Double, longitude: Double, timeZoneId: String) {
        precondition((-90...90).contains(latitude), "Latitude out of range: \(latitude)")
        precondition((-180...180).contains(longitude), "Longitude out of range: \(longitude)")
        precondition(TimeZone(identifier: timeZoneId) != nil, "Unknown time zone: \(timeZoneId)")
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.timeZoneId = timeZoneId
    }
}
