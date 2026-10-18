package io.github.rabayabagum.prayertime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rabayabagum.prayertime.core.Madhab
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerCalculator
import io.github.rabayabagum.prayertime.data.AppState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(state: AppState, onChangeLocation: () -> Unit, onOpenSettings: () -> Unit) {
    // Ticks every second for the countdown; recomputing a day is cheap and offline.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = Instant.now()
        }
    }

    val place = state.location.place
    val zone = place.zone
    val schedule = remember(state, now.epochSecond / 60) {
        PrayerCalculator.day(place, now.atZone(zone).toLocalDate(), state.settings)
    }
    // Per second, so the next prayer switches over the moment its time arrives.
    val next = remember(state, now.epochSecond) {
        PrayerCalculator.upcoming(place, state.settings, now, 1).firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place.name) },
                navigationIcon = {
                    IconButton(onClick = onChangeLocation) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Change location")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { PermissionsCard() }

            item {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(state.location.subtitle, color = secondary)
                    Text(now.longDate(zone))
                    Text(schedule.hijri.formatted, color = secondary)
                    if (schedule.hijri.isRamadan) Text("Imsak ${schedule.imsak.clockTime(zone)}", color = secondary)
                }
            }

            next?.let { event ->
                item {
                    SectionHeader("Next")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clearAndSetSemantics {
                                contentDescription =
                                    "${event.prayer.displayName} at ${event.time.clockTime(zone)}, in ${spokenCountdown(now, event.time)}"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(event.prayer.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(countdown(now, event.time), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            item { SectionHeader("Today") }
            Prayer.entries.forEach { prayer ->
                item {
                    val isNext = next != null && next.prayer == prayer && next.date == schedule.date
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isNext) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics(mergeDescendants = true) { selected = isNext },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val color = if (prayer == Prayer.SUNRISE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        val weight = if (isNext) FontWeight.SemiBold else FontWeight.Normal
                        Text(prayer.displayName, color = color, fontWeight = weight)
                        Text(schedule[prayer].clockTime(zone), color = color, fontWeight = weight)
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(top = 16.dp))
                Column(Modifier.padding(vertical = 8.dp)) {
                    LabeledRow("Method", schedule.method.displayName)
                    if (state.settings.madhab == Madhab.HANAFI) LabeledRow("Asr", "Hanafi")
                    LabeledRow("High latitude", schedule.adjustment.displayName)
                    LabeledRow(
                        "Qibla",
                        "%.0f° from true north".format(PrayerCalculator.qiblaBearing(place.latitude, place.longitude)),
                    )
                }
            }
        }
    }
}

private fun countdown(now: Instant, time: Instant): String {
    val seconds = Duration.between(now, time).seconds.coerceAtLeast(0)
    return "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

private fun spokenCountdown(now: Instant, time: Instant): String {
    val minutes = Duration.between(now, time).toMinutes().coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it hour${if (it == 1L) "" else "s"}" },
        "$m minute${if (m == 1L) "" else "s"}",
    ).joinToString(" ")
}
