package com.example.nexuswallet

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.navigation.toRoute
import com.example.nexuswallet.feature.authentication.ui.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.settings.ui.SecuritySettingsScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.wallet.ui.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.SendScreen
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen
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

    // Collect states
    val wallets by navigationViewModel.wallets.collectAsState()
    val isWalletsLoading by navigationViewModel.isWalletsLoading.collectAsState()
    val shouldNavigateToAuth by navigationViewModel.shouldNavigateToAuth.collectAsState()

    // Handle auth navigation requests
    LaunchedEffect(shouldNavigateToAuth) {
        shouldNavigateToAuth?.let { (screen, walletId) ->
            navController.navigate(AuthenticateRoute(screen, walletId))
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

    // Determine start destination
    val startDestination = if (wallets.isNotEmpty()) MainRoute else WelcomeRoute

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCreateWallet = { navController.navigate(CreateWalletRoute) },
                onImportWallet = { /* TODO */ },
                onSkip = {
                    navController.navigate(MainRoute) {
                        popUpTo<WelcomeRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<MainRoute> {
            MainTabScreen(
                navController = navController,
                navigationViewModel = navigationViewModel
            )
        }

        composable<MarketRoute> {
            MarketScreen(
                navController = navController,
                padding = PaddingValues(0.dp)
            )
        }

        composable<CreateWalletRoute> {
            val viewModel = hiltViewModel<WalletCreationViewModel>()
            WalletCreationScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(navController = navController)
        }

        composable<SecuritySettingsRoute> {
            SecuritySettingsScreen(navController = navController)
        }

        composable<WalletDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<WalletDetailRoute>()
            WalletDetailScreen(
                navController = navController,
                walletId = args.walletId
            )
        }

        composable<CoinDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<CoinDetailRoute>()
            CoinDetailScreen(
                navController = navController,
                walletId = args.walletId,
                coinType = args.coinType
            )
        }

        composable<ReceiveRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<ReceiveRoute>()
            ReceiveScreen(
                navController = navController,
                walletId = args.walletId,
                coinType = args.coinType
            )
        }

        composable<SendRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<SendRoute>()
            SendScreen(
                navController = navController,
                walletId = args.walletId,
                coinType = args.coinType
            )
        }

        composable<ReviewRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<ReviewRoute>()
            TransactionReviewScreen(
                navController = navController,
                walletId = args.walletId,
                coinType = args.coinType,
                toAddress = args.toAddress,
                amount = args.amount,
                feeLevel = args.feeLevel
            )
        }

        composable<TokenDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<TokenDetailRoute>()
            TokenDetailScreen(
                navController = navController,
                tokenId = args.tokenId
            )
        }

        composable<BackupRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<BackupRoute>()
            BackupScreen(
                navController = navController,
                walletId = args.walletId
            )
        }

        composable<AuthenticateRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<AuthenticateRoute>()

            AuthenticationRequiredScreen(
                onAuthenticated = {
                    when (args.screen) {
                        "walletDetail" -> {
                            navController.navigate(WalletDetailRoute(args.walletId)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        "send" -> {
                            // TODO: pass coin type here
                            navController.navigate(SendRoute(args.walletId, CoinType.BITCOIN)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        "backup" -> {
                            navController.navigate(BackupRoute(args.walletId)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        else -> {
                            navController.navigate(MainRoute) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }
    }
}