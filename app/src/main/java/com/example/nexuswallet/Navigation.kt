package com.example.nexuswallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun Navigation(walletDataManager: WalletDataManager = WalletDataManager.getInstance()) {
    val navController = rememberNavController()

    // Check if user has wallets
    val hasWallets by remember { derivedStateOf { walletDataManager.hasWallets() } }

    // Determine start destination
    val startDestination = if (hasWallets) "dashboard" else "welcome"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome Screen (shows first if no wallets)
        composable("welcome") {
            WelcomeScreen(
                onCreateWallet = { navController.navigate("createWallet") },
                onImportWallet = { /* TODO */ },
                onSkip = { navController.navigate("dashboard") }
            )
        }

        // Wallet Dashboard
        composable("dashboard") {
            val viewModel = viewModel<WalletDashboardViewModel>()
            WalletDashboardScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Market Screen
        composable("market") {
            MarketScreen(navController = navController)
        }

        // Wallet Creation
        composable("createWallet") {
            val viewModel = viewModel<WalletCreationViewModel>()
            WalletCreationScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Wallet Detail
        composable(
            route = "walletDetail/{walletId}",
            arguments = listOf(
                navArgument("walletId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            val viewModel = viewModel<WalletDetailViewModel>()

            // Load wallet when screen opens
            LaunchedEffect(walletId) {
                if (walletId.isNotBlank()) {
                    viewModel.loadWallet(walletId)
                }
            }

            WalletDetailScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Token Detail
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