import PrayerCore

extension Prayer {
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

extension HighLatitudeRule {
    var displayName: String {
        switch self {
        case .auto: return "Automatic"
        case .middleOfTheNight: return "Middle of the night"
        case .seventhOfTheNight: return "Seventh of the night"
        case .twilightAngle: return "Twilight angle"
        }
    }
}

extension Shafaq {
    var displayName: String {
        switch self {
        case .general: return "General"
        case .ahmer: return "Red (ahmer)"
        case .abyad: return "White (abyad)"
        }
    }
}

extension Adjustment {
    var displayName: String {
        switch self {
        case .calculated(let rule):
            return rule.displayName
        case .polarFallback(let latitude, let rule):
            return "\(rule.displayName), computed at \(String(format: "%.1f", latitude))°"
        }
    }
}

extension HijriDate {
    private static let months = [
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    ]

    var formatted: String { "\(day) \(Self.months[month - 1]) \(year) AH" }
}
