package com.example.nexuswallet

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nexuswallet.feature.authentication.ui.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.settings.ui.SecuritySettingsScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.wallet.ui.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.SendScreen
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen

//import com.example.nexuswallet.feature.wallet.domain.WalletDataManager
import com.example.nexuswallet.feature.wallet.ui.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.WalletDetailScreen
import com.example.nexuswallet.feature.wallet.ui.WalletDetailViewModel
import com.example.nexuswallet.feature.coin.usdc.USDCSendScreen
import com.example.nexuswallet.feature.wallet.ui.CoinDetailScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Navigation() {
    val navController = rememberNavController()
    val navigationViewModel: NavigationViewModel = hiltViewModel()

    // Collect all states
    val wallets by navigationViewModel.wallets.collectAsState()
    val isWalletsLoading by navigationViewModel.isWalletsLoading.collectAsState()
    val shouldNavigateToAuth by navigationViewModel.shouldNavigateToAuth.collectAsState()

    // Handle auth navigation requests
    LaunchedEffect(shouldNavigateToAuth) {
        shouldNavigateToAuth?.let { (screen, walletId) ->
            navController.navigate("authenticate/$screen/$walletId") {
                launchSingleTop = true
            }
            navigationViewModel.clearAuthNavigation()
        }
    }

    // Show loading screen while checking wallet status
    if (isWalletsLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF3B82F6))
        }
        return
    }

    // Determine start destination based on actual wallet data
    val startDestination = if (wallets.isNotEmpty()) "main" else "welcome"

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
//        composable(
//            route = "walletDetail/{walletId}",
//            arguments = listOf(
//                navArgument("walletId") {
//                    type = NavType.StringType
//                }
//            )
//        ) { backStackEntry ->
//            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
//            val walletViewModel = hiltViewModel<WalletDetailViewModel>()
//            val blockchainViewModel = hiltViewModel<BlockchainViewModel>()
//
//            LaunchedEffect(walletId) {
//                if (walletId.isNotBlank()) {
//                    walletViewModel.loadWallet(walletId)
//                }
//            }
//
//            WalletDetailScreen(
//                navController = navController,
//                walletViewModel = walletViewModel
//            )
//        }

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

        // Receive route
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

        composable(
            route = "walletDetail/{walletId}",
            arguments = listOf(navArgument("walletId") { type = NavType.StringType })
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            WalletDetailScreen(
                navController = navController,
                walletId = walletId
            )
        }

        composable(
            route = "coin/{walletId}/{coinType}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType },
                navArgument("coinType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            val coinTypeString = backStackEntry.arguments?.getString("coinType") ?: ""
            val coinType = try {
                CoinType.valueOf(coinTypeString)
            } catch (e: IllegalArgumentException) {
                CoinType.BITCOIN
            }

            CoinDetailScreen(
                navController = navController,
                walletId = walletId,
                coinType = coinType
            )
        }
        // ===== SEND FLOW SCREENS =====

        composable(
            route = "send/{walletId}/{coinType}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType },
                navArgument("coinType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            val coinType = backStackEntry.arguments?.getString("coinType")?.uppercase() ?: "ETH"

            SendScreen(
                navController = navController,
                walletId = walletId,
                coinType = coinType
            )
        }

        composable(
            route = "review/{walletId}/{coinType}?toAddress={toAddress}&amount={amount}&feeLevel={feeLevel}",
            arguments = listOf(
                navArgument("walletId") { type = NavType.StringType },
                navArgument("coinType") { type = NavType.StringType },
                navArgument("toAddress") { type = NavType.StringType },
                navArgument("amount") { type = NavType.StringType },
                navArgument("feeLevel") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
            val coinType = backStackEntry.arguments?.getString("coinType") ?: "ETH"
            val toAddress = backStackEntry.arguments?.getString("toAddress") ?: ""
            val amount = backStackEntry.arguments?.getString("amount") ?: ""
            val feeLevel = backStackEntry.arguments?.getString("feeLevel")

            TransactionReviewScreen(
                navController = navController,
                walletId = walletId,
                coinType = coinType,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel
            )
        }
    }
}