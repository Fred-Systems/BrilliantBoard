package com.fredsystems.brilliantboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fredsystems.brilliantboard.ui.SettingsViewModel
import com.fredsystems.brilliantboard.ui.navigation.AppNavHost
import com.fredsystems.brilliantboard.ui.theme.AccentThemes
import com.fredsystems.brilliantboard.ui.theme.BrilliantBoardTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settings by settingsViewModel.state.collectAsState()
            BrilliantBoardTheme(
                dynamicColor = settings.settings.dynamicColor,
                accent = AccentThemes.byId(settings.settings.accentTheme).color,
            ) {
                AppNavHost()
            }
        }
    }
}
