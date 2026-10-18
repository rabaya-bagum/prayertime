package io.github.rabayabagum.prayertime.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rabayabagum.prayertime.AppViewModel
import io.github.rabayabagum.prayertime.core.AlertMode
import io.github.rabayabagum.prayertime.core.CustomAngles
import io.github.rabayabagum.prayertime.core.HighLatitudeRule
import io.github.rabayabagum.prayertime.core.Madhab
import io.github.rabayabagum.prayertime.core.Method
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.Shafaq
import io.github.rabayabagum.prayertime.data.AppActions
import io.github.rabayabagum.prayertime.data.AppState
import io.github.rabayabagum.prayertime.notifications.BundledAdhans

private val reminderChoices = listOf(0, 5, 10, 15, 20, 30, 45, 60)

@Composable
fun SettingsScreen(state: AppState, model: AppViewModel, onOpenAbout: () -> Unit, onBack: () -> Unit) {
    val settings = state.settings
    Scaffold(topBar = { BackBar("Settings", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // Calculation
            item {
                SectionHeader("Calculation")
                Choice("Method", settings.method, Method.entries, { it.displayName }) { method ->
                    model.updateSettings { s ->
                        // Custom needs angles before the method switches (PrayerSettings requires them).
                        s.copy(method = method, customAngles = s.customAngles ?: if (method == Method.CUSTOM) CustomAngles(18.0, 17.0) else null)
                    }
                }
                val angles = settings.customAngles
                if (settings.method == Method.CUSTOM && angles != null) {
                    Stepper("Fajr angle", "${angles.fajrAngle}°", angles.fajrAngle > 10, angles.fajrAngle < 25) { delta ->
                        model.updateSettings { it.copy(customAngles = angles.copy(fajrAngle = angles.fajrAngle + delta * 0.5)) }
                    }
                    Stepper(
                        "Isha interval",
                        if (angles.ishaIntervalMinutes == 0) "Use angle" else "${angles.ishaIntervalMinutes} min after Maghrib",
                        angles.ishaIntervalMinutes > 0, angles.ishaIntervalMinutes < 150,
                    ) { delta ->
                        model.updateSettings { it.copy(customAngles = angles.copy(ishaIntervalMinutes = angles.ishaIntervalMinutes + delta * 5)) }
                    }
                    if (angles.ishaIntervalMinutes == 0) {
                        Stepper("Isha angle", "${angles.ishaAngle}°", angles.ishaAngle > 10, angles.ishaAngle < 25) { delta ->
                            model.updateSettings { it.copy(customAngles = angles.copy(ishaAngle = angles.ishaAngle + delta * 0.5)) }
                        }
                    }
                }
                Choice("Asr", settings.madhab, Madhab.entries, { it.displayName }) { m -> model.updateSettings { it.copy(madhab = m) } }
                Choice("High latitude", settings.highLatitudeRule, HighLatitudeRule.entries, { it.displayName }) { r ->
                    model.updateSettings { it.copy(highLatitudeRule = r) }
                }
                if (settings.method == Method.MOONSIGHTING_COMMITTEE) {
                    Choice("Isha twilight", settings.shafaq, Shafaq.entries, { it.displayName }) { sh ->
                        model.updateSettings { it.copy(shafaq = sh) }
                    }
                }
                if (state.settingsCustomized) {
                    TextButton(onClick = model::resetSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Use defaults for ${state.location.subtitle}")
                    }
                }
                FooterText("Mosques differ, sometimes by 40 minutes or more. Pick the method your local mosque follows, then fine-tune each prayer below.")
            }

            // Adjustments
            item {
                SectionHeader("Adjustments")
                Prayer.entries.forEach { prayer ->
                    val minutes = settings.offsetMinutes[prayer] ?: 0
                    Stepper(
                        prayer.displayName,
                        if (minutes == 0) "No change" else "${if (minutes > 0) "+" else ""}$minutes min",
                        minutes > -60, minutes < 60,
                    ) { delta ->
                        model.updateSettings { s ->
                            val value = minutes + delta
                            s.copy(offsetMinutes = if (value == 0) s.offsetMinutes - prayer else s.offsetMinutes + (prayer to value))
                        }
                    }
                }
                val hijri = settings.hijriOffsetDays
                Stepper("Hijri date", if (hijri == 0) "Umm al-Qura" else "${if (hijri > 0) "+" else ""}$hijri day", hijri > -2, hijri < 2) { delta ->
                    model.updateSettings { it.copy(hijriOffsetDays = hijri + delta) }
                }
                val imsak = settings.imsakMinutesBeforeFajr
                Stepper("Imsak", "$imsak min before Fajr", imsak > 0, imsak < 30) { delta ->
                    model.updateSettings { it.copy(imsakMinutesBeforeFajr = imsak + delta * 5) }
                }
            }

            // Adhan alerts
            item {
                SectionHeader("Adhan alerts")
                Prayer.entries.forEach { prayer ->
                    Choice(prayer.displayName, state.notifications.alert(prayer).mode, AlertMode.entries, { it.displayName }) { mode ->
                        model.setAlertMode(prayer, mode)
                    }
                }
                Choice("Reminder before", AppActions.reminderMinutes(state), reminderChoices, { if (it == 0) "None" else "$it min" }) {
                    model.setReminderMinutes(it)
                }
                val sound = state.notifications.alert(Prayer.DHUHR).sound
                AdhanPreview("Adhan voice", sound, prayer = null)
                AdhanPreview("Fajr adhan", sound, prayer = Prayer.FAJR)
                FooterText("Adhans play in full at each prayer time, even when the app is closed. On silent or vibrate you get a silent notification instead.")
            }

            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenAbout).padding(16.dp),
                ) { Text("About", style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

/** A row that opens a menu of choices, like a SwiftUI Picker. */
@Composable
private fun <T> Choice(label: String, selected: T, options: List<T>, name: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        LabeledRow(label, name(selected), Modifier.clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(name(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** A value with − and + buttons; [onStep] gets -1 or +1. */
@Composable
private fun Stepper(label: String, value: String, canDecrease: Boolean, canIncrease: Boolean, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = { onStep(-1) },
            enabled = canDecrease,
            modifier = Modifier.semantics { contentDescription = "Decrease $label" },
        ) { Text("−") }
        OutlinedButton(
            onClick = { onStep(1) },
            enabled = canIncrease,
            modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "Increase $label" },
        ) { Text("+") }
    }
}

/** Plays the bundled recording the alarm would play, so users can hear it before relying on it. */
@Composable
private fun AdhanPreview(title: String, sound: String, prayer: Prayer?) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    val recording = BundledAdhans.recording(sound, prayer)
    val detail = recording?.reciter ?: "Opening takbir only: no Fajr recording yet"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                player?.let { it.release(); player = null; return@clickable }
                val uri = BundledAdhans.adhanUri(context, sound, prayer) ?: return@clickable
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(context, uri)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { it.release(); player = null }
                    prepareAsync()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (player != null) Icons.Default.Close else Icons.Default.PlayArrow,
            contentDescription = if (player != null) "Stop" else "Play",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
