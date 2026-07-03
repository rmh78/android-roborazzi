package com.example.roborazzidemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.roborazzidemo.model.findItemById
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.navigation.NavRoutes
import com.example.roborazzidemo.ui.HomeScreen
import com.example.roborazzidemo.ui.ItemDetailScreen
import com.example.roborazzidemo.ui.ItemListScreen
import com.example.roborazzidemo.ui.ItemNotFoundScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val items = remember { sampleItems() }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home,
    ) {
        composable(NavRoutes.Home) {
            HomeScreen(
                onBrowseItems = { navController.navigate(NavRoutes.Items) },
                onViewSampleDetail = { navController.navigate(NavRoutes.detail(1)) },
            )
        }

        composable(NavRoutes.Items) {
            ItemListScreen(
                items = items,
                onItemClick = { item -> navController.navigate(NavRoutes.detail(item.id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = NavRoutes.Detail,
            arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getInt("itemId")
            val item = itemId?.let(::findItemById)

            if (item != null) {
                ItemDetailScreen(
                    item = item,
                    onBack = { navController.popBackStack() },
                )
            } else {
                ItemNotFoundScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}