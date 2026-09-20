package com.sanket_satpute_20.ironmind.ui.navigation

sealed class Route(val path: String) {
    data object Today : Route("today")
    data object Goals : Route("goals")
    data object History : Route("history")
    data object Settings : Route("settings")
    data object DevControlCenter : Route("dev_control_center")
}
