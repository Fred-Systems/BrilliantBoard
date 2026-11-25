package com.fredsystems.brilliantboard.ui.navigation

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "Home")
    data object MyProfile : Screen("profile", "My Profile")
    data object Scout : Screen("scout", "Scout")
    data object Review : Screen("review", "Review")
    data object Sandbox : Screen("sandbox", "Sandbox")
    data object Settings : Screen("settings", "Settings")
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.MyProfile,
    Screen.Scout,
    Screen.Review,
    Screen.Sandbox,
)
