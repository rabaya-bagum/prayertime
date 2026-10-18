package io.github.rabayabagum.prayertime.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs against the real cities.tsv shared with iOS; mirrors the iOS CityDatabaseTests. */
class CityDatabaseTest {
    private val db = File(System.getProperty("cities.tsv")).reader().use(::CityDatabase)

    @Test
    fun loadsTheWholeFile() {
        assertTrue(db.cities.size > 30_000, "loaded ${db.cities.size}")
    }

    @Test
    fun prefixMatchesComeFirstMostPopulousFirst() {
        val results = db.search("toron")
        assertEquals("Toronto", results.first().name)
        assertEquals("CA", results.first().countryCode)
    }

    @Test
    fun qualifierNarrowsByRegionCountryOrCode() {
        assertEquals("GB", db.search("london").first().countryCode)
        assertEquals("CA", db.search("london, on").first().countryCode)
        assertEquals("CA", db.search("london, ca").first().countryCode)
        assertEquals("Ontario, Canada", db.search("london, canada").first().subtitle)
    }

    @Test
    fun diacriticsAndAsciiNamesBothMatch() {
        assertEquals("Tromsø", db.search("tromso").first().name)
        assertEquals("Tromsø", db.search("Tromsø").first().name)
        assertEquals("Malmö", db.search("malmo").first().name)
    }

    @Test
    fun emptyQueryFindsNothing() {
        assertEquals(emptyList(), db.search("  "))
        assertEquals(emptyList(), db.search(", ca"))
    }

    @Test
    fun nearestCityAndItsZone() {
        // Downtown Toronto: the nearest bundled place may be a neighbourhood like Moss Park.
        assertEquals("America/Toronto", db.nearest(43.65, -79.38)!!.timeZoneId)
        // Just east of 180°: Fiji (178°E) is nearer than Tonga (175°W) only if distance wraps.
        assertEquals("Pacific/Fiji", db.nearest(-18.0, -179.9)!!.timeZoneId)
    }

    @Test
    fun largestCityInAZone() {
        assertEquals("Toronto", db.largest("America/Toronto")!!.name)
    }
}
