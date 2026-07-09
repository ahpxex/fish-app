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
import com.fish.wellness.ui.screen.policyedit.PolicyEditScreen

object Routes {
    const val HOME = "home"
    const val POLICY_EDIT = "policy_edit/{policyId}"
    const val APP_PICKER = "app_picker/{policyId}"

    fun policyEdit(policyId: Long = -1L) = "policy_edit/$policyId"
    fun appPicker(policyId: Long) = "app_picker/$policyId"
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
                onNewPolicy = { navController.navigate(Routes.policyEdit(-1)) },
                onEditPolicy = { id -> navController.navigate(Routes.policyEdit(id)) }
            )
        }

        composable(
            route = Routes.POLICY_EDIT,
            arguments = listOf(navArgument("policyId") { type = NavType.LongType })
        ) {
            PolicyEditScreen(
                onBack = { navController.popBackStack() },
                onSelectApps = { policyId -> navController.navigate(Routes.appPicker(policyId)) }
            )
        }

        composable(
            route = Routes.APP_PICKER,
            arguments = listOf(navArgument("policyId") { type = NavType.LongType })
        ) {
            AppPickerScreen(onBack = { navController.popBackStack() })
        }
    }
}
