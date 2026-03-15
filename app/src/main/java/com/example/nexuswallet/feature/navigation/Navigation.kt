package com.example.nexuswallet.feature.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.nexuswallet.BackupScreen
import com.example.nexuswallet.MainTabScreen
import com.example.nexuswallet.WelcomeScreen
import com.example.nexuswallet.feature.authentication.ui.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.bitcoin.ui.send.BitcoinSendScreen
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.ethereum.ui.EthereumSendScreen
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.settings.ui.SecuritySettingsScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.solana.ui.SolanaSendScreen
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.ui.coindetail.CoinDetailScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
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
    val navController = rememberNavController()
    val navigationViewModel: NavigationViewModel = hiltViewModel()

    val wallets by navigationViewModel.wallets.collectAsState()
    val isWalletsLoading by navigationViewModel.isWalletsLoading.collectAsState()
    val isAuthenticationRequired by navigationViewModel.isAuthenticationRequired.collectAsState()

    if (isWalletsLoading) {
        FullScreenLoading(message = "Loading wallets...")
        return
    }

    val startDestination = if (wallets.isNotEmpty()) {
        MainRoute
    } else {
        WelcomeRoute
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onCreateWallet = {
                    navController.navigate(CreateWalletRoute)
                },
                onImportWallet = {
                    // TODO: Navigate to import wallet screen
                },
                onSkip = {
                    navController.navigate(MainRoute) {
                        popUpTo<WelcomeRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<MainRoute> {
            MainTabScreen(
                onNavigateToCreateWallet = {
                    navController.navigate(CreateWalletRoute)
                },
                onNavigateToWalletDetail = { walletId ->
                    if (isAuthenticationRequired) {
                        navController.navigate(AuthenticateRoute(WalletDetailRoute(walletId)))
                    } else {
                        navController.navigate(WalletDetailRoute(walletId))
                    }
                },
                onNavigateToCoinDetail = { walletId, network ->
                    navController.navigate(CoinDetailRoute(walletId, network))
                },
                onNavigateToTokenDetail = { tokenId ->
                    navController.navigate(TokenDetailRoute(tokenId))
                },
                onNavigateToReceive = { walletId, network ->
                    navController.navigate(ReceiveRoute(walletId, network))
                },
                onNavigateToSend = { walletId, network ->
                    navController.navigate(SendRoute(walletId, network))
                },
                onNavigateToSecurity = {
                    navController.navigate(SecuritySettingsRoute)
                },
                padding = PaddingValues(0.dp)
            )
        }

        composable<MarketRoute> {
            MarketScreen(
                onNavigateToTokenDetail = { tokenId ->
                    navController.navigate(TokenDetailRoute(tokenId))
                },
                padding = PaddingValues(0.dp)
            )
        }

        composable<CreateWalletRoute> {
            val viewModel = hiltViewModel<WalletCreationViewModel>()
            WalletCreationScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                onNavigateToMain = {
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
            SettingsScreen(
                onNavigateToSecurity = {
                    navController.navigate(SecuritySettingsRoute)
                }
            )
        }

        composable<SecuritySettingsRoute> {
            SecuritySettingsScreen(
                onNavigateUp = {
                    navController.navigateUp()
                }
            )
        }

        composable<WalletDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<WalletDetailRoute>()

            WalletDetailScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                onNavigateToCoinDetail = { walletId, network ->
                    navController.navigate(CoinDetailRoute(walletId, network))
                },
                onNavigateToReceive = { walletId, network ->
                    navController.navigate(ReceiveRoute(walletId, network))
                },
                onNavigateToSend = { walletId, network ->
                    navController.navigate(SendRoute(walletId, network))
                },
                onNavigateToAllTransactions = { walletId ->
                    // TODO: Navigate to all transactions screen
                },
                onNavigateToTransactionDetail = { walletId, transactionId ->
                    navController.navigate(TransactionDetailRoute(walletId, transactionId))
                },
                walletId = args.walletId
            )
        }

        composable<CoinDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<CoinDetailRoute>()

            CoinDetailScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                onNavigateToReceive = { walletId, network ->
                    navController.navigate(ReceiveRoute(walletId, network))
                },
                onNavigateToSend = { walletId, network ->
                    navController.navigate(SendRoute(walletId, network))
                },
                onNavigateToAllTransactions = { walletId, network ->
                    // TODO: Navigate to all transactions screen
                },
                onNavigateToTransactionDetail = { walletId, transactionId ->
                    navController.navigate(TransactionDetailRoute(walletId, transactionId))
                },
                walletId = args.walletId,
                network = args.network
            )
        }

        composable<TransactionDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionDetailRoute>()

            TransactionDetailScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                walletId = args.walletId,
                transactionId = args.transactionId
            )
        }

        composable<ReceiveRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<ReceiveRoute>()

            ReceiveScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                walletId = args.walletId,
                network = args.network
            )
        }

        composable<SendRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<SendRoute>()

            when (args.network) {
                is BitcoinNetwork -> {
                    BitcoinSendScreen(
                        onNavigateUp = {
                            navController.navigateUp()
                        },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, network ->
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    network = network
                                )
                            ) {
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        },
                        walletId = args.walletId,
                        network = args.network
                    )
                }

                is EthereumNetwork -> {
                    when (args.network.coinType) {
                        CoinType.ETHEREUM, CoinType.USDC -> {
                            EthereumSendScreen(
                                onNavigateUp = {
                                    navController.navigateUp()
                                },
                                onNavigateToReview = { walletId, toAddress, amount, feeLevel, network ->
                                    navController.navigate(
                                        ReviewRoute(
                                            walletId = walletId,
                                            toAddress = toAddress,
                                            amount = amount,
                                            feeLevel = feeLevel?.name,
                                            network = network
                                        )
                                    ) {
                                        popUpTo<SendRoute> { inclusive = true }
                                    }
                                },
                                walletId = args.walletId,
                                network = args.network
                            )
                        }

                        else -> {}
                    }
                }

                is SolanaNetwork -> {
                    SolanaSendScreen(
                        onNavigateUp = {
                            navController.navigateUp()
                        },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, network ->
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    network = network
                                )
                            ) {
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

            TransactionReviewScreen(
                onNavigateUp = {
                    navController.navigate(WalletDetailRoute(args.walletId)) {
                        popUpTo(WalletDetailRoute(args.walletId)) { inclusive = true }
                    }
                },
                onNavigateToWalletDetail = { walletId ->
                    navController.navigate(WalletDetailRoute(walletId)) {
                        popUpTo(WalletDetailRoute(walletId)) { inclusive = true }
                    }
                },
                walletId = args.walletId,
                toAddress = args.toAddress,
                amount = args.amount,
                feeLevel = feeLevel.toString(),
                network = args.network
            )
        }

        composable<TokenDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<TokenDetailRoute>()

            TokenDetailScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                tokenId = args.tokenId
            )
        }

        composable<BackupRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<BackupRoute>()

            BackupScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
                walletId = args.walletId
            )
        }

        composable<AuthenticateRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<AuthenticateRoute>()

            AuthenticationRequiredScreen(
                onAuthenticated = {
                    navController.navigate(args.target) {
                        popUpTo<AuthenticateRoute> { inclusive = true }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                },
                canAuthenticate = canAuthenticate
            )
        }
    }
}