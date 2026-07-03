package com.example.roborazzidemo.navigation

import androidx.navigation.NavHostController
import com.example.roborazzidemo.model.findItemById

class VoiceNavigationHandler(
    private val navController: NavHostController,
) {
    fun navigateToScreen(destination: String, itemId: Int?): NavigationResult {
        return when (destination) {
            "home" -> {
                navController.navigate(NavRoutes.Home) {
                    popUpTo(NavRoutes.Home) { inclusive = true }
                    launchSingleTop = true
                }
                NavigationResult.Success("Navigated to home.")
            }
            "items" -> {
                navController.navigate(NavRoutes.Items) {
                    launchSingleTop = true
                }
                NavigationResult.Success("Navigated to items list.")
            }
            "detail" -> {
                val id = itemId
                    ?: return NavigationResult.Failure("item_id is required for detail navigation.")
                navController.navigate(NavRoutes.detail(id)) {
                    launchSingleTop = true
                }
                val found = findItemById(id) != null
                if (found) {
                    NavigationResult.Success("Navigated to item $id detail.")
                } else {
                    NavigationResult.Success("Navigated to item not found for id $id.")
                }
            }
            else -> NavigationResult.Failure("Unknown destination: $destination")
        }
    }

    fun navigateBack(): NavigationResult {
        val popped = navController.popBackStack()
        return if (popped) {
            NavigationResult.Success("Went back to the previous screen.")
        } else {
            NavigationResult.Failure("Already on the root screen.")
        }
    }
}

sealed class NavigationResult {
    data class Success(val message: String) : NavigationResult()
    data class Failure(val message: String) : NavigationResult()

    fun asToolOutput(): String = when (this) {
        is Success -> message
        is Failure -> message
    }
}