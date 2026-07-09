package com.fish.wellness.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fish.wellness.ui.screen.apppicker.AppPickerScreen
import com.fish.wellness.ui.screen.home.HomeScreen
import com.fish.wellness.ui.screen.schedule.ScheduleEditScreen

object Routes {
    const val HOME = "home"
    const val APP_PICKER = "app_picker"
    const val SCHEDULE_EDIT = "schedule_edit/{scheduleId}"

    fun scheduleEdit(scheduleId: Long = -1L) = "schedule_edit/$scheduleId"
}

@Composable
fun FishApp() {
    val navController = rememberNavController()
    FishNavGraph(navController)
}

@Composable
fun FishNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onPickApps = { navController.navigate(Routes.APP_PICKER) },
                onAddSchedule = { navController.navigate(Routes.scheduleEdit(-1)) },
                onEditSchedule = { id -> navController.navigate(Routes.scheduleEdit(id)) }
            )
        }

        composable(Routes.APP_PICKER) {
            AppPickerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.SCHEDULE_EDIT,
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getLong("scheduleId") ?: -1L
            ScheduleEditScreen(
                scheduleId = scheduleId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
