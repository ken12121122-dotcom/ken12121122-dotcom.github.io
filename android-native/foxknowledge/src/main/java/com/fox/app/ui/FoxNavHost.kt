package com.fox.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fox.app.ui.detail.NodeDetailScreen
import com.fox.app.ui.home.HomeScreen
import com.fox.app.ui.search.SearchScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{nodeId}"
    fun detail(nodeId: String) = "detail/$nodeId"
}

@Composable
fun FoxNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToDetail = { nodeId -> navController.navigate(Routes.detail(nodeId)) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onNavigateToDetail = { nodeId -> navController.navigate(Routes.detail(nodeId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getString("nodeId").orEmpty()
            NodeDetailScreen(
                nodeId = nodeId,
                onNavigateToDetail = { targetId -> navController.navigate(Routes.detail(targetId)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
