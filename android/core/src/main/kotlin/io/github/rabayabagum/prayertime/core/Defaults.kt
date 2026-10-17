package io.github.rabayabagum.prayertime.core

/**
 * Onboarding preselection from the device's country code. This is a product
 * choice, not a published standard: the user is still asked "which mosque or
 * method do you follow?" and this is only the highlighted answer.
 */
object Defaults {
    private val hanafiSouthAsia = setOf("PK", "IN", "BD", "AF", "NP", "LK")

    fun settingsFor(countryCode: String?): PrayerSettings = when (countryCode?.uppercase()) {
        // Masjids in Canada and the US use 12°, 15° or 18°. Adhan recommends
        // Moonsighting Committee over ISNA for North America.
        "CA", "US" -> PrayerSettings(Method.MOONSIGHTING_COMMITTEE)
        "SA", "YE" -> PrayerSettings(Method.UMM_AL_QURA)
        "AE" -> PrayerSettings(Method.DUBAI)
        "KW" -> PrayerSettings(Method.KUWAIT)
        "QA", "BH", "OM" -> PrayerSettings(Method.QATAR)
        "EG", "SD", "LY", "SY", "LB", "IQ", "JO", "PS" -> PrayerSettings(Method.EGYPTIAN)
        "TR" -> PrayerSettings(Method.TURKEY)
        "SG", "MY", "BN", "ID" -> PrayerSettings(Method.SINGAPORE)
        in hanafiSouthAsia -> PrayerSettings(Method.KARACHI, madhab = Madhab.HANAFI)
        else -> PrayerSettings(Method.MUSLIM_WORLD_LEAGUE)
    }
}
