package io.github.rabayabagum.prayertime

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.rabayabagum.prayertime.qibla.HeadingSensor
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Drives the real app on a device or emulator through its main flows, matching
 * the iOS PrayerTimeUITests. The orchestrator clears app data before each test.
 */
@RunWith(AndroidJUnit4::class)
class PrayerTimeUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    // Granted up front so no system dialog interrupts the flow.
    @get:Rule
    val permissions: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        HeadingSensor.testHeading = null
        HeadingSensor.testNoCompass = false
    }

    @Test
    fun searchForACityAndCustomizeTheMethod() {
        launch()
        chooseCity("tromso", "Tromsø")

        // Times must be in Tromsø's zone whatever the device's: Dhuhr there is always near midday.
        val dhuhr = rowText("Dhuhr")
        assertTrue(dhuhr, Regex("""Dhuhr (11|12|1):\d\d""").containsMatchIn(dhuhr))

        list().performScrollToNode(hasText("Seventh of the night", substring = true))
        compose.assertShown("Seventh of the night", "Tromsø is above 48°")

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Method").performClick()
        compose.onNodeWithText("ISNA (North America)").performClick()
        compose.waitForText("Use defaults for Troms, Norway")

        compose.onNodeWithText("Use defaults for Troms, Norway").performClick()
        compose.waitForText("Muslim World League")
    }

    @Test
    fun useCurrentLocation() {
        launch()
        compose.onNodeWithContentDescription("Change location").performClick()
        withMockLocation(43.6512, -79.3471) {
            compose.onNodeWithText("Use current location").performClick()
            // The picker closes on a fix and Today shows the nearest bundled city.
            compose.waitForText("South Riverdale", timeoutMillis = 25_000)
        }
        compose.assertShown("Ontario, Canada", "subtitle of the located city")
    }

    @Test
    fun qiblaCompassGuidesToTheQibla() {
        HeadingSensor.testHeading = 0.0
        launch()
        chooseCity("toronto", "Toronto")
        openQibla()
        // The dial carries the guidance as its state: what TalkBack reads.
        val turn = dialState()
        assertTrue(turn, turn.startsWith("Turn right"))
        compose.assertShown("° W", "Toronto's declination is west")

        // Magnetic 65° in Toronto is ~55° true, the Qibla bearing.
        scenario?.close()
        HeadingSensor.testHeading = 65.0
        launch()
        openQibla()
        compose.waitUntil(5_000) { dialState() == "Facing the Qibla" }
    }

    @Test
    fun qiblaWithoutCompassShowsBearing() {
        HeadingSensor.testNoCompass = true
        launch()
        openQibla()
        compose.waitForText("This device has no compass", substring = true)
    }

    @Test
    fun settingsPreviewsBothAdhans() {
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        list().performScrollToNode(hasText("Fajr adhan"))
        compose.assertShown("Aaqib Azeez", "regular adhan reciter")
        compose.assertShown("Besim Azemi", "Fajr adds \"As-salatu khayrun min an-nawm\", so it has its own recording")
    }

    // Helpers

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun chooseCity(query: String, name: String) {
        compose.onNodeWithContentDescription("Change location").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(query)
        compose.waitForText(name)
        compose.onAllNodesWithText(name).onFirst().performClick()
        // Back on Today: the location button is there again and the title is the city.
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Change location").fetchSemanticsNodes().isNotEmpty() }
        compose.assertShown(name, "title")
    }

    private fun openQibla() {
        compose.onNode(hasText("Qibla") and isSelectable()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Qibla", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun dialState(): String =
        compose.onNodeWithContentDescription("Qibla compass").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.StateDescription).orEmpty()

    /** A Today row's merged text, e.g. "Dhuhr 12:29 PM". */
    private fun rowText(prayer: String): String =
        compose.onAllNodes(hasText(prayer)).onFirst().fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")

    private fun list(): SemanticsNodeInteraction = compose.onNode(hasScrollAction())

    /**
     * Serves a fixed position from a mock network provider while [block] runs, so
     * the test doesn't depend on the emulator's own location.
     */
    private fun withMockLocation(latitude: Double, longitude: Double, block: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("cmd location set-location-enabled true")
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = LocationManager.NETWORK_PROVIDER
        @Suppress("DEPRECATION") // The ProviderProperties overload needs API 31; this runs on 26+.
        manager.addTestProvider(provider, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE)
        manager.setTestProviderEnabled(provider, true)
        val serving = AtomicBoolean(true)
        // getCurrentLocation wants a fresh fix after it asks, so keep supplying one.
        val feeder = thread {
            while (serving.get()) {
                val fix = Location(provider).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    accuracy = 100f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                runCatching { manager.setTestProviderLocation(provider, fix) }
                Thread.sleep(500)
            }
        }
        try {
            block()
        } finally {
            serving.set(false)
            feeder.join()
            runCatching { manager.removeTestProvider(provider) }
        }
    }

    private fun shell(command: String) {
        // Reading to the end waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }
}

private fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeoutMillis: Long = 5_000) =
    waitUntil(timeoutMillis) { onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }

private fun ComposeTestRule.assertShown(text: String, why: String) =
    assertTrue("\"$text\" not shown: $why", onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty())
