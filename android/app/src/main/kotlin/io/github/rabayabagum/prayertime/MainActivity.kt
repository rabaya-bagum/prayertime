package io.github.rabayabagum.prayertime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rabayabagum.prayertime.ui.AboutScreen
import io.github.rabayabagum.prayertime.ui.LocationScreen
import io.github.rabayabagum.prayertime.ui.PrayerTimeTheme
import io.github.rabayabagum.prayertime.ui.SettingsScreen
import io.github.rabayabagum.prayertime.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrayerTimeTheme { PrayerTimeAppContent() }
        }
    }
}

private enum class Screen { TODAY, LOCATION, SETTINGS, ABOUT }

@Composable
private fun PrayerTimeAppContent(model: AppViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }

    // Coming back to the app re-arms the alarm window, as iOS refreshes on becoming active.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.resync() }

    BackHandler(enabled = screen != Screen.TODAY) {
        screen = if (screen == Screen.ABOUT) Screen.SETTINGS else Screen.TODAY
    }

    when (screen) {
        Screen.TODAY -> TodayScreen(
            state = state,
            onChangeLocation = { screen = Screen.LOCATION },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
        Screen.LOCATION -> LocationScreen(
            model = model,
            onChosen = { model.setLocation(it); screen = Screen.TODAY },
            onBack = { screen = Screen.TODAY },
        )
        Screen.SETTINGS -> SettingsScreen(
            state = state,
            model = model,
            onOpenAbout = { screen = Screen.ABOUT },
            onBack = { screen = Screen.TODAY },
        )
        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
    }
}
