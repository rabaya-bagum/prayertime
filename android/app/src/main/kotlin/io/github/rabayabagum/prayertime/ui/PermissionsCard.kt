package io.github.rabayabagum.prayertime.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.rabayabagum.prayertime.notifications.PrayerScheduler

/**
 * The three things that stop an adhan on Android, asked for one at a time and
 * re-checked whenever the app returns from system settings (docs/NOTIFICATIONS.md):
 * notification permission, exact alarms, then battery optimization.
 */
@Composable
fun PermissionsCard() {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks++ }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }

    // Read `checks` so this recomposes after each resume or permission result.
    val step = checks.let { missingStep(context) } ?: return

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(step.title, style = MaterialTheme.typography.titleMedium)
            Text(step.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 4.dp)) {
                TextButton(
                    onClick = {
                        when (step) {
                            Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            Step.EXACT_ALARMS -> PrayerScheduler.exactAlarmSettingsIntent(context)?.let(context::startActivity)
                            Step.BATTERY -> context.startActivity(
                                PrayerScheduler.batteryOptimizationSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) { Text(step.action) }
                // Battery optimization is advice, not a requirement, so it can be dismissed.
                if (step == Step.BATTERY) {
                    TextButton(onClick = { dismissBatteryStep(context); checks++ }) { Text("Not now") }
                }
            }
        }
    }
}

private enum class Step(val title: String, val explanation: String, val action: String) {
    NOTIFICATIONS(
        "Allow notifications",
        "Without notification permission, adhans and reminders can't sound or appear.",
        "Allow",
    ),
    EXACT_ALARMS(
        "Allow alarms on time",
        "Android needs the \"Alarms & reminders\" permission to sound the adhan at the exact minute. Without it, alerts can arrive up to 5 minutes late and play a short clip instead of the full adhan.",
        "Open settings",
    ),
    BATTERY(
        "Keep the adhan from being silenced",
        "Battery optimization can stop the adhan on some phones, especially Xiaomi and Samsung. In the list, find Prayer Time and choose \"Not optimized\" or \"Unrestricted\".",
        "Open battery settings",
    ),
}

private fun missingStep(context: Context): Step? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
        Step.NOTIFICATIONS
    !PrayerScheduler.canScheduleExactAlarms(context) -> Step.EXACT_ALARMS
    !PrayerScheduler.isIgnoringBatteryOptimizations(context) && !batteryStepDismissed(context) -> Step.BATTERY
    else -> null
}

private fun prefs(context: Context) = context.getSharedPreferences("prayertime_onboarding", Context.MODE_PRIVATE)

private fun batteryStepDismissed(context: Context) = prefs(context).getBoolean("battery_dismissed", false)

private fun dismissBatteryStep(context: Context) = prefs(context).edit().putBoolean("battery_dismissed", true).apply()
