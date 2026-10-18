package io.github.rabayabagum.prayertime.data

import java.io.Reader
import java.text.Normalizer
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos

/** One row of the bundled GeoNames extract (see tools/gen_cities.py). */
data class City(
    val name: String,
    val region: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val population: Int,
    /** GeoNames' ASCII spelling, so "tromso" finds Tromsø; empty when the same as [name]. */
    val asciiName: String,
) {
    /** "Ontario, Canada": enough to tell London, England from London, Ontario. */
    val subtitle: String
        get() = if (region.isEmpty() || region == name) country else "$region, $country"
}

/**
 * Offline city search and nearest-city lookup over cities.tsv, the same file and
 * rules as the iOS app's CityDatabase. About 34k rows, so a linear scan is fast enough.
 */
class CityDatabase(reader: Reader) {
    val cities: List<City>
    /** Folded name, plus the folded ASCII name after a tab when it differs. */
    private val searchKeys: List<String>

    init {
        val knownZones = ZoneId.getAvailableZoneIds()
        cities = reader.useLines { lines ->
            lines.mapNotNull { line ->
                val f = line.split('\t')
                if (f.size != 9) return@mapNotNull null
                val lat = f[4].toDoubleOrNull() ?: return@mapNotNull null
                val lon = f[5].toDoubleOrNull() ?: return@mapNotNull null
                // Place requires a zone the OS knows; GeoNames occasionally runs ahead of tzdata.
                if (f[6] !in knownZones) return@mapNotNull null
                City(f[0], f[1], f[2], f[3], lat, lon, f[6], f[7].toIntOrNull() ?: 0, f[8])
            }.toList()
        }
        searchKeys = cities.map { if (it.asciiName.isEmpty()) fold(it.name) else fold(it.name) + "\t" + fold(it.asciiName) }
    }

    /**
     * Cities whose name starts with the query, then those containing it, most
     * populous first. Text after a comma narrows by region, country or code:
     * "london, on" or "london ca".
     */
    fun search(query: String, limit: Int = 50): List<City> {
        val parts = query.split(",", limit = 2).map(::fold)
        val name = parts.firstOrNull().orEmpty()
        if (name.isEmpty()) return emptyList()
        val qualifier = parts.getOrElse(1) { "" }

        val prefix = mutableListOf<City>()
        val contains = mutableListOf<City>()
        for ((i, key) in searchKeys.withIndex()) {
            if (prefix.size >= limit) break
            val at = key.indexOf(name)
            if (at < 0) continue
            val city = cities[i]
            if (qualifier.isNotEmpty() && !matches(city, qualifier)) continue
            if (at == 0 || key[at - 1] == '\t') prefix += city else contains += city
        }
        return (prefix + contains).take(limit)
    }

    fun nearest(latitude: Double, longitude: Double): City? =
        cities.minByOrNull { distance(it, latitude, longitude) }

    /** The largest city in a zone, used as a starting place before the user picks one. */
    fun largest(inTimeZone: String): City? = cities.firstOrNull { it.timeZoneId == inTimeZone }

    private fun matches(city: City, qualifier: String): Boolean =
        fold(city.countryCode) == qualifier || fold(city.region).startsWith(qualifier) || fold(city.country).startsWith(qualifier)

    /** Squared equirectangular distance: only used for ranking, and accurate enough at city scale. */
    private fun distance(city: City, lat: Double, lon: Double): Double {
        var dLon = abs(city.longitude - lon)
        if (dLon > 180) dLon = 360 - dLon
        val x = dLon * cos(Math.toRadians((city.latitude + lat) / 2))
        val y = city.latitude - lat
        return x * x + y * y
    }

    companion object {
        private val combiningMarks = Regex("\\p{Mn}+")

        /** Case-, diacritic- and width-insensitive, like Foundation's folding on iOS. */
        fun fold(s: String): String =
            combiningMarks.replace(Normalizer.normalize(s, Normalizer.Form.NFKD), "").lowercase().trim()
    }
}
