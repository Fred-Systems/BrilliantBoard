package com.fredsystems.brilliantboard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredsystems.brilliantboard.R
import com.fredsystems.brilliantboard.ui.screens.HomeScreen
import com.fredsystems.brilliantboard.ui.screens.MyProfileScreen
import com.fredsystems.brilliantboard.ui.screens.ReviewScreen
import com.fredsystems.brilliantboard.ui.screens.SandboxScreen
import com.fredsystems.brilliantboard.ui.screens.ScoutScreen
import com.fredsystems.brilliantboard.ui.screens.SettingsScreen
import com.fredsystems.brilliantboard.ui.screens.SplashScreen
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isSplash = currentRoute == Screen.Splash.route

    Scaffold(
        topBar = {
            if (!isSplash) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                Screen.MyProfile.route -> "My Profile"
                                Screen.Scout.route -> "Opponent Scout"
                                Screen.Review.route -> "Game Review"
                                Screen.Sandbox.route -> "Analysis Board"
                                Screen.Settings.route -> "Settings"
                                else -> "BrilliantBoard"
                            },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark,
                        titleContentColor = Accent,
                        navigationIconContentColor = Accent,
                    ),
                )
            }
        },
        bottomBar = {
            if (!isSplash) {
                NavigationBar {
                    (bottomNavItems + Screen.Settings).forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        when (screen) {
                                            Screen.Home -> R.drawable.ic_home
                                            Screen.MyProfile -> R.drawable.ic_profile
                                            Screen.Scout -> R.drawable.ic_scout
                                            Screen.Review -> R.drawable.ic_review
                                            Screen.Sandbox -> R.drawable.ic_board
                                            Screen.Settings -> R.drawable.ic_settings
                                            else -> R.drawable.ic_launcher
                                        }
                                    ),
                                    contentDescription = screen.label,
                                )
                            },
                            label = { Text(screen.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B0C10), Color(0xFF0F1620), Color(0xFF08090C)),
                    ),
                ),
        ) {
            NavHost(
                navController,
                startDestination = Screen.Splash.route,
            ) {
            composable(Screen.Splash.route) {
                SplashScreen(onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) { HomeScreen(onNavigate = { navController.navigate(it) }) }
            composable(Screen.MyProfile.route) { MyProfileScreen() }
            composable(Screen.Scout.route) { ScoutScreen() }
            composable(Screen.Review.route) { ReviewScreen() }
            composable(Screen.Sandbox.route) { SandboxScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}
