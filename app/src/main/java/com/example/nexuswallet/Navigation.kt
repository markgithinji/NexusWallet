package com.example.nexuswallet

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.nexuswallet.feature.authentication.ui.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.bitcoin.ui.send.BitcoinSendScreen
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.ui.EthereumSendScreen
import com.example.nexuswallet.feature.coin.solana.ui.SolanaSendScreen
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.settings.ui.SecuritySettingsScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.wallet.ui.CoinDetailScreen
import com.example.nexuswallet.feature.wallet.ui.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.TransactionDetailScreen
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen
import com.example.nexuswallet.feature.wallet.ui.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.WalletDetailScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Navigation(
    canAuthenticate: Boolean
) {
    Log.d("Navigation", "=== Navigation Composable Recomposition ===")

    val navController = rememberNavController()
    val navigationViewModel: NavigationViewModel = hiltViewModel()

    // Collect states
    val wallets by navigationViewModel.wallets.collectAsState()
    val isWalletsLoading by navigationViewModel.isWalletsLoading.collectAsState()
    val shouldNavigateToAuth by navigationViewModel.shouldNavigateToAuth.collectAsState()

    // Log state changes
    LaunchedEffect(wallets, isWalletsLoading) {
        Log.d("Navigation", "State - wallets: ${wallets.size}, isLoading: $isWalletsLoading")
        if (wallets.isNotEmpty()) {
            wallets.forEachIndexed { index, wallet ->
                Log.d("Navigation", "  Wallet $index: id=${wallet.id}, name=${wallet.name}")
            }
        }
    }

    val handleDirectNavigation = { screen: String, walletId: String ->
        when (screen) {
            "walletDetail" -> {
                Log.d("Navigation", "Direct navigation to WalletDetailRoute - walletId: $walletId")
                navController.navigate(WalletDetailRoute(walletId)) {
                    // Pop up to the authentication screen and remove it
                    popUpTo<AuthenticateRoute> { inclusive = true }
                }
            }
            "send" -> {
                Log.d("Navigation", "Direct navigation to SendRoute - walletId: $walletId")
                navController.navigate(SendRoute(walletId, CoinType.BITCOIN)) {
                    popUpTo<AuthenticateRoute> { inclusive = true }
                }
            }
            "backup" -> {
                Log.d("Navigation", "Direct navigation to BackupRoute - walletId: $walletId")
                navController.navigate(BackupRoute(walletId)) {
                    popUpTo<AuthenticateRoute> { inclusive = true }
                }
            }
            else -> {
                Log.d("Navigation", "Direct navigation to MainRoute")
                navController.navigate(MainRoute) {
                    popUpTo<AuthenticateRoute> { inclusive = true }
                }
            }
        }
    }

    // Handle auth navigation requests
    LaunchedEffect(shouldNavigateToAuth) {
        shouldNavigateToAuth?.let { (screen, walletId) ->
            Log.d("Navigation", "Auth navigation requested - screen: $screen, walletId: $walletId")
            navController.navigate(AuthenticateRoute(screen, walletId))
            navigationViewModel.clearAuthNavigation()
        }
    }

    // Show loading screen while checking wallet status
    if (isWalletsLoading) {
        Log.d("Navigation", "Showing loading screen - wallets: ${wallets.size}, isLoading: $isWalletsLoading")
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }

    // Determine start destination
    val startDestination = if (wallets.isNotEmpty()) {
        Log.d("Navigation", "Start destination: MainRoute (${wallets.size} wallets found)")
        MainRoute
    } else {
        Log.d("Navigation", "Start destination: WelcomeRoute (no wallets found)")
        WelcomeRoute
    }

    Log.d("Navigation", "Building NavHost with startDestination: $startDestination")

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<WelcomeRoute> {
            Log.d("Navigation", "Navigated to WelcomeRoute")
            WelcomeScreen(
                onCreateWallet = {
                    Log.d("Navigation", "WelcomeScreen: onCreateWallet clicked")
                    navController.navigate(CreateWalletRoute)
                },
                onImportWallet = {
                    Log.d("Navigation", "WelcomeScreen: onImportWallet clicked")
                    // TODO: Navigate to import wallet screen
                },
                onSkip = {
                    Log.d("Navigation", "WelcomeScreen: onSkip clicked")
                    navController.navigate(MainRoute) {
                        popUpTo<WelcomeRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<MainRoute> {
            Log.d("Navigation", "Navigated to MainRoute")
            MainTabScreen(
                onNavigateToCreateWallet = {
                    Log.d("Navigation", "MainTabScreen: navigate to CreateWalletRoute")
                    navController.navigate(CreateWalletRoute)
                },
                onNavigateToWalletDetail = { walletId ->
                    Log.d("Navigation", "MainTabScreen: navigate to WalletDetailRoute - walletId: $walletId")
                    handleDirectNavigation("walletDetail", walletId)
                },
                onNavigateToCoinDetail = { walletId, coinType, network ->
                    Log.d("Navigation", "MainTabScreen: navigate to CoinDetailRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(CoinDetailRoute(walletId, coinType, network))
                },
                onNavigateToTokenDetail = { tokenId ->
                    Log.d("Navigation", "MainTabScreen: navigate to TokenDetailRoute - tokenId: $tokenId")
                    navController.navigate(TokenDetailRoute(tokenId))
                },
                onNavigateToReceive = { walletId, coinType, network ->
                    Log.d("Navigation", "MainTabScreen: navigate to ReceiveRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(ReceiveRoute(walletId, coinType, network))
                },
                onNavigateToSend = { walletId, coinType, network ->
                    Log.d("Navigation", "MainTabScreen: navigate to SendRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(SendRoute(walletId, coinType, network))
                },
                onNavigateToSecurity = {
                    Log.d("Navigation", "MainTabScreen: navigate to SecuritySettingsRoute")
                    navController.navigate(SecuritySettingsRoute)
                },
                onRequestAuthentication = { screen, walletId ->
                    navigationViewModel.requestAuthentication(screen, walletId)
                },
                padding = PaddingValues(0.dp)
            )
        }

        composable<MarketRoute> {
            Log.d("Navigation", "Navigated to MarketRoute")
            MarketScreen(
                onNavigateToTokenDetail = { tokenId ->
                    Log.d("Navigation", "MarketScreen: navigate to TokenDetailRoute - tokenId: $tokenId")
                    navController.navigate(TokenDetailRoute(tokenId))
                },
                padding = PaddingValues(0.dp)
            )
        }

        composable<CreateWalletRoute> {
            Log.d("Navigation", "Navigated to CreateWalletRoute")
            val viewModel = hiltViewModel<WalletCreationViewModel>()
            WalletCreationScreen(
                onNavigateUp = {
                    Log.d("Navigation", "WalletCreationScreen: navigate up")
                    navController.navigateUp()
                },
                onNavigateToMain = {
                    Log.d("Navigation", "WalletCreationScreen: navigate to Main")
                    navController.navigate(MainRoute) {
                        popUpTo(MainRoute) {
                            inclusive = false
                        }
                    }
                },
                viewModel = viewModel
            )
        }

        composable<SettingsRoute> {
            Log.d("Navigation", "Navigated to SettingsRoute")
            SettingsScreen(
                onNavigateToSecurity = {
                    Log.d("Navigation", "SettingsScreen: navigate to SecuritySettingsRoute")
                    navController.navigate(SecuritySettingsRoute)
                }
            )
        }

        composable<SecuritySettingsRoute> {
            Log.d("Navigation", "Navigated to SecuritySettingsRoute")
            SecuritySettingsScreen(
                onNavigateUp = {
                    Log.d("Navigation", "SecuritySettingsScreen: navigate up")
                    navController.navigateUp()
                }
            )
        }

        composable<WalletDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<WalletDetailRoute>()
            Log.d("Navigation", "Navigated to WalletDetailRoute - walletId: ${args.walletId}")

            WalletDetailScreen(
                onNavigateUp = {
                    Log.d("Navigation", "WalletDetailScreen: navigate up")
                    navController.navigateUp()
                },
                onNavigateToCoinDetail = { walletId, coinType, network ->
                    Log.d("Navigation", "WalletDetailScreen: navigate to CoinDetailRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(CoinDetailRoute(walletId, coinType, network))
                },
                onNavigateToReceive = { walletId, coinType, network ->
                    Log.d("Navigation", "WalletDetailScreen: navigate to ReceiveRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(ReceiveRoute(walletId, coinType, network))
                },
                onNavigateToSend = { walletId, coinType, network ->
                    Log.d("Navigation", "WalletDetailScreen: navigate to SendRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(SendRoute(walletId, coinType, network))
                },
                onNavigateToAllTransactions = { walletId ->
                    Log.d("Navigation", "WalletDetailScreen: navigate to AllTransactions - walletId: $walletId (TODO)")
                    // TODO: Navigate to all transactions screen
                },
                onNavigateToTransactionDetail = { walletId, transactionId, coinType ->
                    Log.d("Navigation", "WalletDetailScreen: navigate to TransactionDetailRoute - walletId: $walletId, transactionId: $transactionId, coinType: $coinType")
                    navController.navigate(TransactionDetailRoute(walletId, transactionId, coinType))
                },
                walletId = args.walletId
            )
        }

        composable<CoinDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<CoinDetailRoute>()
            Log.d("Navigation", "Navigated to CoinDetailRoute - walletId: ${args.walletId}, coinType: ${args.coinType}, network: ${args.network}")

            CoinDetailScreen(
                onNavigateUp = {
                    Log.d("Navigation", "CoinDetailScreen: navigate up")
                    navController.navigateUp()
                },
                onNavigateToReceive = { walletId, coinType, network ->
                    Log.d("Navigation", "CoinDetailScreen: navigate to ReceiveRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(ReceiveRoute(walletId, coinType, network))
                },
                onNavigateToSend = { walletId, coinType, network ->
                    Log.d("Navigation", "CoinDetailScreen: navigate to SendRoute - walletId: $walletId, coinType: $coinType, network: $network")
                    navController.navigate(SendRoute(walletId, coinType, network))
                },
                onNavigateToAllTransactions = { walletId, coinType, network ->
                    Log.d("Navigation", "CoinDetailScreen: navigate to AllTransactions - walletId: $walletId, coinType: $coinType, network: $network")
                    // TODO: Navigate to all transactions screen
                },
                onNavigateToTransactionDetail = { walletId, transactionId, coinType ->
                    Log.d("Navigation", "CoinDetailScreen: navigate to TransactionDetailRoute - walletId: $walletId, transactionId: $transactionId, coinType: $coinType")
                    navController.navigate(TransactionDetailRoute(walletId, transactionId, coinType))
                },
                walletId = args.walletId,
                coinType = args.coinType,
                network = args.network
            )
        }

        composable<TransactionDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionDetailRoute>()
            Log.d("Navigation", "Navigated to TransactionDetailRoute - walletId: ${args.walletId}, transactionId: ${args.transactionId}, coinType: ${args.coinType}")

            TransactionDetailScreen(
                onNavigateUp = {
                    Log.d("Navigation", "TransactionDetailScreen: navigate up")
                    navController.navigateUp()
                },
                walletId = args.walletId,
                transactionId = args.transactionId,
                coinType = args.coinType
            )
        }

        composable<ReceiveRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<ReceiveRoute>()
            Log.d("Navigation", "Navigated to ReceiveRoute - walletId: ${args.walletId}, coinType: ${args.coinType}, network: ${args.network}")

            ReceiveScreen(
                onNavigateUp = {
                    Log.d("Navigation", "ReceiveScreen: navigate up")
                    navController.navigateUp()
                },
                walletId = args.walletId,
                coinType = args.coinType,
                network = args.network
            )
        }

        composable<SendRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<SendRoute>()
            Log.d("Navigation", "Navigated to SendRoute - walletId: ${args.walletId}, coinType: ${args.coinType}, network: ${args.network}")

            when (args.coinType) {
                CoinType.BITCOIN -> {
                    _root_ide_package_.com.example.nexuswallet.feature.bitcoin.ui.send.BitcoinSendScreen(
                        onNavigateUp = {
                            Log.d("Navigation", "BitcoinSendScreen: navigate up")
                            navController.navigateUp()
                        },
                        onNavigateToReview = { walletId, coinType, toAddress, amount, feeLevel, network ->
                            Log.d("Navigation", "BitcoinSendScreen: navigate to ReviewRoute")
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    coinType = coinType,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    network = network
                                )
                            ) {
                                // Remove the send screen from back stack
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        },
                        walletId = args.walletId,
                        network = args.network
                    )
                }
                CoinType.ETHEREUM, CoinType.USDC -> {
                    EthereumSendScreen(
                        onNavigateUp = {
                            Log.d("Navigation", "EthereumSendScreen: navigate up")
                            navController.navigateUp()
                        },
                        onNavigateToReview = { walletId, coinType, toAddress, amount, feeLevel, network ->
                            Log.d("Navigation", "EthereumSendScreen: navigate to ReviewRoute")
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    coinType = coinType,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    network = network
                                )
                            ) {
                                // Remove the send screen from back stack
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        },
                        walletId = args.walletId,
                        coinType = args.coinType,
                        network = args.network
                    )
                }
                CoinType.SOLANA -> {
                    SolanaSendScreen(
                        onNavigateUp = {
                            Log.d("Navigation", "SolanaSendScreen: navigate up")
                            navController.navigateUp()
                        },
                        onNavigateToReview = { walletId, coinType, toAddress, amount, feeLevel, network ->
                            Log.d("Navigation", "SolanaSendScreen: navigate to ReviewRoute")
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    coinType = coinType,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    network = network
                                )
                            ) {
                                // Remove the send screen from back stack
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        },
                        walletId = args.walletId,
                        network = args.network
                    )
                }
            }
        }

        composable<ReviewRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<ReviewRoute>()
            val feeLevel = args.feeLevel?.let { FeeLevel.valueOf(it) }

            Log.d("Navigation", "Navigated to ReviewRoute - " +
                    "walletId: ${args.walletId}, coinType: ${args.coinType}, " +
                    "toAddress: ${args.toAddress}, amount: ${args.amount}, feeLevel: ${args.feeLevel}, network: ${args.network}")

            TransactionReviewScreen(
                onNavigateUp = {
                    Log.d("Navigation", "TransactionReviewScreen: navigate up")
                    // Navigate up to wallet detail
                    navController.navigate(WalletDetailRoute(args.walletId)) {
                        popUpTo(WalletDetailRoute(args.walletId)) { inclusive = true }
                    }
                },
                onNavigateToWalletDetail = { walletId ->
                    Log.d("Navigation", "TransactionReviewScreen: navigate to WalletDetailRoute - walletId: $walletId")
                    navController.navigate(WalletDetailRoute(walletId)) {
                        popUpTo(WalletDetailRoute(walletId)) { inclusive = true }
                    }
                },
                walletId = args.walletId,
                coinType = args.coinType,
                toAddress = args.toAddress,
                amount = args.amount,
                feeLevel = feeLevel.toString(),
                network = args.network
            )
        }

        composable<TokenDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<TokenDetailRoute>()
            Log.d("Navigation", "Navigated to TokenDetailRoute - tokenId: ${args.tokenId}")

            TokenDetailScreen(
                onNavigateUp = {
                    Log.d("Navigation", "TokenDetailScreen: navigate up")
                    navController.navigateUp()
                },
                tokenId = args.tokenId
            )
        }

        composable<BackupRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<BackupRoute>()
            Log.d("Navigation", "Navigated to BackupRoute - walletId: ${args.walletId}")

            BackupScreen(
                onNavigateUp = {
                    Log.d("Navigation", "BackupScreen: navigate up")
                    navController.navigateUp()
                },
                walletId = args.walletId
            )
        }

        composable<AuthenticateRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<AuthenticateRoute>()
            Log.d("Navigation", "Navigated to AuthenticateRoute - screen: ${args.screen}, walletId: ${args.walletId}")

            AuthenticationRequiredScreen(
                onAuthenticated = {
                    Log.d("Navigation", "Authentication successful - navigating to: ${args.screen}")
                    handleDirectNavigation(args.screen, args.walletId)
                },
                onCancel = {
                    Log.d("Navigation", "Authentication cancelled")
                    navController.popBackStack()
                },
                canAuthenticate = canAuthenticate
            )
        }
    }

    // Add a side effect to log navigation changes
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            Log.d("Navigation", "Current destination: ${backStackEntry.destination.route}")
        }
    }
}