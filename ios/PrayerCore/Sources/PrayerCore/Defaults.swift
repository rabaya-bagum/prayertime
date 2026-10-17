/// Onboarding preselection from the device's country code. This is a product
/// choice, not a published standard: the user is still asked "which mosque or
/// method do you follow?" and this is only the highlighted answer.
/// Keep in step with android/core's Defaults.
public enum Defaults {
    private static let hanafiSouthAsia: Set<String> = ["PK", "IN", "BD", "AF", "NP", "LK"]

    public static func settings(forCountry countryCode: String?) -> PrayerSettings {
        switch countryCode?.uppercased() {
        // Masjids in Canada and the US use 12°, 15° or 18°. Adhan recommends
        // Moonsighting Committee over ISNA for North America.
        case "CA", "US": return PrayerSettings(method: .moonsightingCommittee)
        case "SA", "YE": return PrayerSettings(method: .ummAlQura)
        case "AE": return PrayerSettings(method: .dubai)
        case "KW": return PrayerSettings(method: .kuwait)
        case "QA", "BH", "OM": return PrayerSettings(method: .qatar)
        case "EG", "SD", "LY", "SY", "LB", "IQ", "JO", "PS": return PrayerSettings(method: .egyptian)
        case "TR": return PrayerSettings(method: .turkey)
        case "SG", "MY", "BN", "ID": return PrayerSettings(method: .singapore)
        case let code? where hanafiSouthAsia.contains(code): return PrayerSettings(method: .karachi, madhab: .hanafi)
        default: return PrayerSettings(method: .muslimWorldLeague)
        }
    }
}
