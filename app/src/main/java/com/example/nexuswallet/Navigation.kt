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
import com.example.nexuswallet.feature.authentication.ui.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.settings.ui.SecuritySettingsScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
//import com.example.nexuswallet.feature.wallet.domain.WalletDataManager
import com.example.nexuswallet.feature.wallet.ui.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.WalletDetailScreen
import com.example.nexuswallet.feature.wallet.ui.WalletDetailViewModel

@Composable
fun Navigation() {
    val navController = rememberNavController()

    // Get the wallet repository
    val walletRepository = WalletRepository.getInstance()

    // Check if user has wallets - using the repository
    val hasWallets by remember {
        derivedStateOf { walletRepository.hasWallets() }
    }

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
            MarketScreen(
                navController = navController,
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

        composable("securitySettings") {
            SecuritySettingsScreen(
                navController = navController
            )
        }

        composable("settings") {
            SettingsScreen(
                navController = navController
            )
        }

        composable(
            route = "authenticate/{screen}/{walletId}",
            arguments = listOf(
                navArgument("screen") { type = NavType.StringType },
                navArgument("walletId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val screen = backStackEntry.arguments?.getString("screen") ?: ""
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""

            AuthenticationRequiredScreen(
                onAuthenticated = {
                    // Navigate to the appropriate screen after authentication
                    when (screen) {
                        "walletDetail" -> {
                            navController.navigate("walletDetail/$walletId") {
                                popUpTo("authenticate/{screen}/{walletId}") {
                                    inclusive = true
                                }
                            }
                        }

                        "send" -> {
                            navController.navigate("send/$walletId") {
                                popUpTo("authenticate/{screen}/{walletId}") {
                                    inclusive = true
                                }
                            }
                        }

                        "backup" -> {
                            navController.navigate("backup/$walletId") {
                                popUpTo("authenticate/{screen}/{walletId}") {
                                    inclusive = true
                                }
                            }
                        }

                        else -> {
                            // Fallback to main
                            navController.navigate("main") {
                                popUpTo("authenticate/{screen}/{walletId}") {
                                    inclusive = true
                                }
                            }
                        }
                    }
                },
                onCancel = {
                    navController.navigateUp()
                }
            )
        }

        composable(
            route = "send/{walletId}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            SendScreen(
                navController = navController,
                walletId = walletId
            )
        }

        // Add backup route (will be protected by authentication)
        composable(
            route = "backup/{walletId}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            BackupScreen(
                navController = navController,
                walletId = walletId
            )
        }

        // Add receive route (no authentication needed)
        composable(
            route = "receive/{walletId}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            ReceiveScreen(
                navController = navController,
                walletId = walletId
            )
        }
    }
}