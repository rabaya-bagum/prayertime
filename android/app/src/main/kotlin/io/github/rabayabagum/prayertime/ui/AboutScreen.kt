package io.github.rabayabagum.prayertime.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.rabayabagum.prayertime.notifications.BundledAdhans

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    Scaffold(topBar = { BackBar("About", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Privacy")
            Text(
                "Everything is calculated on your phone. Prayer Time has no account, no ads and no analytics, and it never sends your location anywhere.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionHeader("Credits")
            Credit("City data: GeoNames", "Licensed under CC BY 4.0", "https://www.geonames.org")
            BundledAdhans.credits.forEach {
                Credit("Adhan: ${it.reciter}", "${it.credit}, ${it.license}. Trimmed and normalized.", it.source)
            }
            Credit("Prayer calculation: Adhan", "Batoul Apps, MIT License", "https://github.com/batoulapps/adhan-java")

            LabeledRow("Version", version)
        }
    }
}

@Composable
private fun Credit(title: String, detail: String, url: String) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
