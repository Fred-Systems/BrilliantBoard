package com.fredsystems.brilliantboard.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                val current = navController.currentBackStackEntryAsState().value?.destination?.route
                (bottomNavItems + Screen.Settings).forEach { screen ->
                    NavigationBarItem(
                        selected = current == screen.route,
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
                                    }
                                ),
                                contentDescription = screen.label,
                            )
                        },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(padding)) {
            composable(Screen.Home.route) { HomeScreen(onNavigate = { navController.navigate(it) }) }
            composable(Screen.MyProfile.route) { MyProfileScreen() }
            composable(Screen.Scout.route) { ScoutScreen() }
            composable(Screen.Review.route) { ReviewScreen() }
            composable(Screen.Sandbox.route) { SandboxScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
