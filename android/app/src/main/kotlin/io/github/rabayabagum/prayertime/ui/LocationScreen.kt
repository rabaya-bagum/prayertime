package io.github.rabayabagum.prayertime.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rabayabagum.prayertime.AppViewModel
import io.github.rabayabagum.prayertime.data.City
import io.github.rabayabagum.prayertime.data.SavedLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LocationScreen(model: AppViewModel, onChosen: (SavedLocation) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<City>()) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        delay(150) // let typing settle before scanning 34k cities
        results = model.searchCities(query)
    }

    fun locate() = scope.launch {
        locating = true
        error = null
        val location = model.locateNearestCity()
        locating = false
        if (location != null) onChosen(location) else error = "Couldn't find your location. Search for your city instead."
    }

    // Approximate only: prayer times move about 30 s per few km, so a city-level fix is plenty.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else error = "Location access is off. You can search for your city instead."
    }

    Scaffold(topBar = { BackBar("Location", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search cities") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locating) { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Use current location", Modifier.padding(start = 16.dp).weight(1f), color = MaterialTheme.colorScheme.primary)
                    if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                FooterText(error ?: "Approximate location only. It never leaves your phone.")
            }
            if (results.isNotEmpty()) item { SectionHeader("Cities") }
            items(results) { city ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChosen(SavedLocation.of(city)) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(city.name, style = MaterialTheme.typography.bodyLarge)
                    Text(city.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (results.isEmpty() && query.isNotBlank()) item { FooterText("No cities match \"$query\".") }
        }
    }
}
