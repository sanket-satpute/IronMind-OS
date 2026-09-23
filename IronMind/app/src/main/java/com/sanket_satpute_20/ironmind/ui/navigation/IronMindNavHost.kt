package com.sanket_satpute_20.ironmind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sanket_satpute_20.ironmind.ui.IronMindAppState
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalsScreen
import com.sanket_satpute_20.ironmind.ui.screens.settings.SettingsScreen
import com.sanket_satpute_20.ironmind.ui.screens.history.HistoryScreen
import com.sanket_satpute_20.ironmind.ui.screens.today.TodayScreen
import com.sanket_satpute_20.ironmind.ui.screens.dev.DevelopmentControlCenterScreen
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalDetailScreen
import com.sanket_satpute_20.ironmind.ui.screens.plans.PlanDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument

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
            GoalsScreen(
                onNavigateToGoal = { id ->
                    appState.navController.navigate(Route.GoalDetail(id).path)
                }
            )
        }
        composable(
            route = Route.GoalDetail.path,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            GoalDetailScreen(
                goalId = id,
                onNavigateBack = { appState.navController.popBackStack() },
                onNavigateToPlan = { planId ->
                    appState.navController.navigate(Route.PlanDetail(planId).path)
                }
            )
        }
        composable(
            route = Route.PlanDetail.path,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            PlanDetailScreen(
                planId = id,
                onNavigateBack = { appState.navController.popBackStack() }
            )
        }
        composable(Route.History.path) {
            HistoryScreen()
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                onNavigateToDevControlCenter = {
                    appState.navController.navigate(Route.DevControlCenter.path)
                }
            )
        }
        composable(Route.DevControlCenter.path) {
            DevelopmentControlCenterScreen()
        }
    }
}
