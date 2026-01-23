package com.example.nexuswallet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
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
    val startDestination = if (hasWallets) "main" else "welcome"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome Screen
        composable("welcome") {
            WelcomeScreen(
                onCreateWallet = { navController.navigate("createWallet") },
                onImportWallet = { /* TODO */ },
                onSkip = {
                    navController.navigate("main") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        // Main App with Tabs
        composable("main") {
            MainTabScreen(navController = navController)
        }

        // Market Screen (accessible from tabs)
        composable("market") {
            MarketScreen(navController = navController,
                padding = PaddingValues(0.dp)
                )
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