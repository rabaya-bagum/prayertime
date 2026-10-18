package io.github.rabayabagum.prayertime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rabayabagum.prayertime.qibla.QiblaScreen
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

private enum class Screen { TODAY, QIBLA, LOCATION, SETTINGS, ABOUT }

@Composable
private fun PrayerTimeAppContent(model: AppViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }

    // Coming back to the app re-arms the alarm window, as iOS refreshes on becoming active.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.resync() }

    BackHandler(enabled = screen != Screen.TODAY) {
        screen = if (screen == Screen.ABOUT) Screen.SETTINGS else Screen.TODAY
    }

    // Times and Qibla are tabs, as on iOS; the other screens open over Times.
    val tab = screen == Screen.TODAY || screen == Screen.QIBLA
    Scaffold(
        // The bar handles the navigation-bar inset itself; inner screens handle the rest.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tab) {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.TODAY,
                        onClick = { screen = Screen.TODAY },
                        icon = { Icon(painterResource(R.drawable.ic_tab_times), contentDescription = null) },
                        label = { Text("Times") },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.QIBLA,
                        onClick = { screen = Screen.QIBLA },
                        icon = { Icon(painterResource(R.drawable.ic_tab_qibla), contentDescription = null) },
                        label = { Text("Qibla") },
                    )
                }
            }
        },
    ) { padding ->
        // Each screen has its own top bar; only the bottom bar's space is handed down,
        // and marked consumed so inner screens don't pad the navigation bar again.
        val barSpace = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(Modifier.padding(barSpace).consumeWindowInsets(barSpace)) {
            when (screen) {
                Screen.TODAY -> TodayScreen(
                    state = state,
                    onChangeLocation = { screen = Screen.LOCATION },
                    onOpenSettings = { screen = Screen.SETTINGS },
                )
                Screen.QIBLA -> QiblaScreen(state)
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
    }
}
