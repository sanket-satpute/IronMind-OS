package com.sanket_satpute_20.ironmind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sanket_satpute_20.ironmind.ui.IronMindAppState
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalsScreen
import com.sanket_satpute_20.ironmind.ui.screens.settings.SettingsScreen
import com.sanket_satpute_20.ironmind.ui.screens.today.TodayScreen

@Composable
fun IronMindNavHost(
    appState: IronMindAppState,
    modifier: Modifier = Modifier,
    startDestination: String = Route.Today.path
) {
    NavHost(
        navController = appState.navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Route.Today.path) {
            TodayScreen()
        }
        composable(Route.Goals.path) {
            GoalsScreen()
        }
        composable(Route.Settings.path) {
            SettingsScreen()
        }
    }
}
