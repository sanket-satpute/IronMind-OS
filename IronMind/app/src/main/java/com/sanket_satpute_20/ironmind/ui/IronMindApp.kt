package com.sanket_satpute_20.ironmind.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanket_satpute_20.ironmind.ui.navigation.IronMindNavHost
import com.sanket_satpute_20.ironmind.ui.navigation.Route

@Composable
fun IronMindApp(
    appState: IronMindAppState = rememberIronMindAppState()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            IronMindBottomBar(
                currentRoute = appState.currentDestination?.route,
                onNavigateToRoute = appState::navigateToTopLevelDestination
            )
        }
    ) { innerPadding ->
        IronMindNavHost(
            appState = appState,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun IronMindBottomBar(
    currentRoute: String?,
    onNavigateToRoute: (String) -> Unit
) {
    NavigationBar {
        val destinations = listOf(
            Triple(Route.Today.path, "Today", Icons.Default.DateRange),
            Triple(Route.Goals.path, "Goals", Icons.Default.List),
            Triple(Route.Settings.path, "Settings", Icons.Default.Settings)
        )

        destinations.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToRoute(route) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(text = label) }
            )
        }
    }
}
