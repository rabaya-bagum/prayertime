package io.github.rabayabagum.prayertime

import android.annotation.SuppressLint
import android.app.Application
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rabayabagum.prayertime.core.AlertMode
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerSettings
import io.github.rabayabagum.prayertime.data.AppActions
import io.github.rabayabagum.prayertime.data.AppState
import io.github.rabayabagum.prayertime.data.City
import io.github.rabayabagum.prayertime.data.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PrayerTimeApplication

    private val _state = MutableStateFlow(app.loadState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var resyncJob: Job? = null

    fun setLocation(location: SavedLocation) = update { AppActions.setLocation(it, location) }

    fun updateSettings(change: (PrayerSettings) -> PrayerSettings) = update { AppActions.updateSettings(it, change) }

    fun resetSettings() = update(AppActions::resetSettings)

    fun setAlertMode(prayer: Prayer, mode: AlertMode) = update { AppActions.setAlertMode(it, prayer, mode) }

    fun setReminderMinutes(minutes: Int) = update { AppActions.setReminderMinutes(it, minutes) }

    suspend fun searchCities(query: String): List<City> = withContext(Dispatchers.Default) { app.cities.search(query) }

    /** Called when the app comes to the foreground, as on iOS. */
    fun resync() = app.resync()

    /**
     * One coarse fix, matched to the nearest bundled city for its name and zone.
     * Null when no provider is on or none answers in 20 s.
     */
    @SuppressLint("MissingPermission") // The caller only calls this after ACCESS_COARSE_LOCATION is granted.
    suspend fun locateNearestCity(): SavedLocation? {
        val manager = app.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val fix = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, locationExecutor) { location ->
                    continuation.resume(location)
                }
            }
        } ?: manager.getLastKnownLocation(provider) ?: return null
        return withContext(Dispatchers.Default) {
            app.cities.nearest(fix.latitude, fix.longitude)?.let { SavedLocation.fix(fix.latitude, fix.longitude, it) }
        }
    }

    private fun update(change: (AppState) -> AppState) {
        val next = change(_state.value)
        if (next == _state.value) return
        _state.value = next
        app.saveState(next)
        // Coalesce rapid edits (stepper taps) into one re-arm.
        resyncJob?.cancel()
        resyncJob = viewModelScope.launch {
            delay(400)
            app.resync()
        }
    }

    private companion object {
        val locationExecutor = Executors.newSingleThreadExecutor()
    }
}
