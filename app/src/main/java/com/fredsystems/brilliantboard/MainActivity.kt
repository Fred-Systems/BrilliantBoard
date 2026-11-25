package com.fredsystems.brilliantboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fredsystems.brilliantboard.ui.navigation.AppNavHost
import com.fredsystems.brilliantboard.ui.theme.BrilliantBoardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BrilliantBoardTheme {
                AppNavHost()
            }
        }
    }
}
