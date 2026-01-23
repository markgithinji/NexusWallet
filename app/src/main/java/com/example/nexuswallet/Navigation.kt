package com.example.nexuswallet

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun Navigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(navController = navController)
        }
        composable("market") {
            MarketScreen(navController = navController)
        }
        composable(
            route = "token/{tokenId}",
            arguments = listOf(
                navArgument("tokenId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val tokenId = backStackEntry.arguments?.getString("tokenId") ?: "bitcoin"
            TokenDetailScreen(
                navController = navController,
                tokenId = tokenId
            )
        }
    }
}