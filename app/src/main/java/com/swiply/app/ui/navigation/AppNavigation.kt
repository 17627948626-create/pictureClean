package com.swiply.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swiply.app.ui.screens.DeleteConfirmScreen
import com.swiply.app.ui.screens.PhotoSwipeScreen
import com.swiply.app.viewmodel.PhotoViewModel

sealed class Screen(val route: String) {
    object PhotoSwipe : Screen("photo_swipe")
    object DeleteConfirm : Screen("delete_confirm")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: PhotoViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.PhotoSwipe.route
    ) {
        composable(Screen.PhotoSwipe.route) {
            PhotoSwipeScreen(
                viewModel = viewModel,
                onNavigateToConfirm = {
                    navController.navigate(Screen.DeleteConfirm.route)
                }
            )
        }
        composable(Screen.DeleteConfirm.route) {
            DeleteConfirmScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
