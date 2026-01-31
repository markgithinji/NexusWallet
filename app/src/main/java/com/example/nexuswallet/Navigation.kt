package com.example.nexuswallet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.example.nexuswallet.feature.wallet.ui.ApiDebugScreen
import com.example.nexuswallet.feature.wallet.ui.BlockchainViewModel
import com.example.nexuswallet.feature.wallet.ui.FullQrCodeScreen
import com.example.nexuswallet.feature.wallet.ui.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.SendScreen
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen
import com.example.nexuswallet.feature.wallet.ui.TransactionStatusScreen
//import com.example.nexuswallet.feature.wallet.domain.WalletDataManager
import com.example.nexuswallet.feature.wallet.ui.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.WalletDetailScreen
import com.example.nexuswallet.feature.wallet.ui.WalletDetailViewModel

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val navigationViewModel: NavigationViewModel = hiltViewModel()

    // Determine if user has wallets
    val hasWallets by navigationViewModel.hasWallets.collectAsState()

    // Watch for auth navigation requests
    val shouldNavigateToAuth by navigationViewModel.shouldNavigateToAuth.collectAsState()

    // Handle auth navigation requests
    LaunchedEffect(shouldNavigateToAuth) {
        shouldNavigateToAuth?.let { (screen, walletId) ->
            // Navigate to auth screen first
            navController.navigate("authenticate/$screen/$walletId") {
                // Don't add to back stack if already on same screen
                launchSingleTop = true
            }

            // Clear the navigation request
            navigationViewModel.clearAuthNavigation()
        }
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
            MainTabScreen(
                navController = navController,
                navigationViewModel = navigationViewModel
            )
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
            val viewModel = hiltViewModel<WalletCreationViewModel>()
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
            val walletViewModel = hiltViewModel<WalletDetailViewModel>()
            val blockchainViewModel = hiltViewModel<BlockchainViewModel>()

            LaunchedEffect(walletId) {
                if (walletId.isNotBlank()) {
                    walletViewModel.loadWallet(walletId)
                }
            }

            WalletDetailScreen(
                navController = navController,
                walletViewModel = walletViewModel,
                blockchainViewModel = blockchainViewModel
            )
        }

        composable(
            route = "qrCode/{walletId}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            FullQrCodeScreen(
                navController = navController,
                walletId = walletId
            )
        }

        composable(
            route = "debug/{walletId}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            ApiDebugScreen(navController, walletId)
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

        composable("receive/{walletId}") { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            ReceiveScreen(navController, walletId)
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

        // Add backup route
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

        // Add receive route
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

        // ===== SEND FLOW SCREENS =====

        // Send Screen (Address & Amount Input)
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

        // Transaction Review Screen
        composable(
            route = "review/{transactionId}",
            arguments = listOf(
                navArgument("transactionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            TransactionReviewScreen(
                navController = navController,
                transactionId = transactionId
            )
        }

        // Transaction Status Screen
        composable(
            route = "status/{transactionId}",
            arguments = listOf(
                navArgument("transactionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            TransactionStatusScreen(
                navController = navController,
                transactionId = transactionId
            )
        }
    }
}