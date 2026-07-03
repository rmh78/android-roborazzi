package com.example.roborazzidemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.roborazzidemo.model.findItemById
import com.example.roborazzidemo.model.sampleItems
import com.example.roborazzidemo.navigation.NavRoutes
import com.example.roborazzidemo.semantics.ScreenElement
import com.example.roborazzidemo.semantics.TrackScreenContent
import com.example.roborazzidemo.ui.HomeScreen
import com.example.roborazzidemo.ui.ItemDetailScreen
import com.example.roborazzidemo.ui.ItemListScreen
import com.example.roborazzidemo.ui.ItemNotFoundScreen
import com.example.roborazzidemo.viewmodel.ItemListScrollController

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    scrollController: ItemListScrollController? = null,
    modifier: Modifier = Modifier,
) {
    val items = remember { sampleItems() }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home,
        modifier = modifier,
    ) {
        composable(NavRoutes.Home) {
            TrackScreenContent(
                route = NavRoutes.Home,
                elements = listOf(
                    ScreenElement("heading", stringResource(R.string.home_title)),
                    ScreenElement("text", stringResource(R.string.home_subtitle)),
                    ScreenElement("button", stringResource(R.string.home_browse_items)),
                    ScreenElement("button", stringResource(R.string.home_view_sample_detail)),
                ),
            )
            HomeScreen(
                onBrowseItems = { navController.navigate(NavRoutes.Items) },
                onViewSampleDetail = { navController.navigate(NavRoutes.detail(1)) },
            )
        }

        composable(NavRoutes.Items) {
            TrackScreenContent(
                route = NavRoutes.Items,
                elements = buildList {
                    add(ScreenElement("heading", stringResource(R.string.items_title)))
                    add(ScreenElement("button", stringResource(R.string.back)))
                    items.forEach { item ->
                        add(
                            ScreenElement(
                                role = "list_item",
                                text = item.title,
                                description = stringResource(item.descriptionRes),
                            ),
                        )
                    }
                },
            )
            ItemListScreen(
                items = items,
                onItemClick = { item -> navController.navigate(NavRoutes.detail(item.id)) },
                onBack = { navController.popBackStack() },
                scrollController = scrollController,
            )
        }

        composable(
            route = NavRoutes.Detail,
            arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getInt("itemId")
            val item = itemId?.let(::findItemById)

            if (item != null) {
                TrackScreenContent(
                    route = NavRoutes.detail(item.id),
                    elements = listOf(
                        ScreenElement("heading", item.title),
                        ScreenElement("button", stringResource(R.string.back)),
                        ScreenElement(
                            role = "text",
                            text = stringResource(item.descriptionRes),
                        ),
                    ),
                )
                ItemDetailScreen(
                    item = item,
                    onBack = { navController.popBackStack() },
                )
            } else {
                TrackScreenContent(
                    route = "not_found",
                    elements = listOf(
                        ScreenElement("heading", stringResource(R.string.item_not_found_title)),
                        ScreenElement("button", stringResource(R.string.back)),
                        ScreenElement("text", stringResource(R.string.item_not_found_message)),
                    ),
                )
                ItemNotFoundScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}