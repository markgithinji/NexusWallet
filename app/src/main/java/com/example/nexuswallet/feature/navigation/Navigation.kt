package com.example.nexuswallet.feature.navigation

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.nexuswallet.BackupScreen
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
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen
import com.example.nexuswallet.feature.wallet.ui.coindetail.CoinDetailScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.recive.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.transactiondetail.TransactionDetailScreen
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WelcomeScreen
import com.example.nexuswallet.feature.wallet.ui.walletdetail.WalletDetailScreen
import kotlinx.serialization.json.Json
import kotlin.collections.mapOf
import kotlin.reflect.typeOf

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Navigation(
    canAuthenticate: Boolean
) {
    val navController = rememberNavController()
    val navigationViewModel: NavigationViewModel = hiltViewModel()

    val wallets by navigationViewModel.wallets.collectAsStateWithLifecycle()
    val isWalletsLoading by navigationViewModel.isWalletsLoading.collectAsStateWithLifecycle()
    val isAuthenticationRequired by navigationViewModel.isAuthenticationRequired.collectAsStateWithLifecycle()

    if (isWalletsLoading) {
        FullScreenLoading(message = "Loading wallets...")
        return
    }

    val startDestination = if (wallets.isNotEmpty()) {
        MainRoute
    } else {
        WelcomeRoute
    }

    val networkTypeMap = remember {
        mapOf(
            typeOf<Network>() to NetworkNavType
        )
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
                        navController.navigate(
                            AuthenticateRoute(
                                targetRoute = WalletDetailRoute(walletId).toString()
                            )
                        )
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
                onNavigateToTokenDetail = {
                    navController.navigate(TokenDetailRoute(it))
                },
                padding = PaddingValues(0.dp)
            )
        }

        composable<CreateWalletRoute> {
            val viewModel = hiltViewModel<WalletCreationViewModel>()

            WalletCreationScreen(
                viewModel = viewModel,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToMain = {
                    navController.navigate(MainRoute) {
                        popUpTo(MainRoute) { inclusive = false }
                    }
                }
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
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<WalletDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<WalletDetailRoute>()

            WalletDetailScreen(
                walletId = args.walletId,
                onNavigateUp = { navController.navigateUp() },
                onAssetClick = { walletId, network ->
                    navController.navigate(CoinDetailRoute(walletId, network))
                },
                onReceiveClick = { walletId, network ->
                    navController.navigate(ReceiveRoute(walletId, network))
                },
                onSendClick = { walletId, network ->
                    navController.navigate(SendRoute(walletId, network))
                },
                onNavigateToAllTransactions = { walletId ->
                    // TODO: Navigate to all transactions screen
                },
                onNavigateToTransactionDetail = { walletId, txId ->
                    navController.navigate(TransactionDetailRoute(walletId, txId))
                }
            )
        }

        composable<CoinDetailRoute>(
            typeMap = networkTypeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<CoinDetailRoute>()

            CoinDetailScreen(
                walletId = args.walletId,
                network = args.network,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToReceive = { walletId, network ->
                    navController.navigate(ReceiveRoute(walletId, network))
                },
                onNavigateToSend = { walletId, network ->
                    navController.navigate(SendRoute(walletId, network))
                },
                onNavigateToAllTransactions = { walletId, network ->
                    // TODO: Navigate to all transactions screen
                },
                onNavigateToTransactionDetail = { walletId, txId ->
                    navController.navigate(TransactionDetailRoute(walletId, txId))
                }
            )
        }

        composable<TransactionDetailRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionDetailRoute>()

            TransactionDetailScreen(
                walletId = args.walletId,
                transactionId = args.transactionId,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<ReceiveRoute>(
            typeMap = networkTypeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<ReceiveRoute>()

            ReceiveScreen(
                walletId = args.walletId,
                network = args.network,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<SendRoute>(
            typeMap = networkTypeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<SendRoute>()

            when (args.network) {

                is BitcoinNetwork -> {
                    BitcoinSendScreen(
                        walletId = args.walletId,
                        network = args.network,
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, network ->

                            navController.navigate(
                                ReviewRoute(
                                    walletId,
                                    toAddress,
                                    amount,
                                    feeLevel?.name,
                                    network
                                )
                            ) {
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        }
                    )
                }

                is EthereumNetwork -> {
                    EthereumSendScreen(
                        walletId = args.walletId,
                        network = args.network,
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, network ->

                            navController.navigate(
                                ReviewRoute(
                                    walletId,
                                    toAddress,
                                    amount,
                                    feeLevel?.name,
                                    network
                                )
                            ) {
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        }
                    )
                }

                is SolanaNetwork -> {
                    SolanaSendScreen(
                        walletId = args.walletId,
                        network = args.network,
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, network ->

                            navController.navigate(
                                ReviewRoute(
                                    walletId,
                                    toAddress,
                                    amount,
                                    feeLevel?.name,
                                    network
                                )
                            ) {
                                popUpTo<SendRoute> { inclusive = true }
                            }
                        }
                    )
                }
            }
        }

        composable<ReviewRoute>(
            typeMap = networkTypeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<ReviewRoute>()
            val feeLevel = args.feeLevel?.let { FeeLevel.valueOf(it) }

            TransactionReviewScreen(
                walletId = args.walletId,
                toAddress = args.toAddress,
                amount = args.amount,
                feeLevel = feeLevel.toString(),
                network = args.network,
                onNavigateUp = {
                    navController.navigate(WalletDetailRoute(args.walletId)) {
                        popUpTo(WalletDetailRoute(args.walletId)) { inclusive = true }
                    }
                },
                onNavigateToWalletDetail = { walletId ->
                    navController.navigate(WalletDetailRoute(walletId)) {
                        popUpTo(WalletDetailRoute(walletId)) { inclusive = true }
                    }
                }
            )
        }

        composable<TokenDetailRoute> { backStackEntry ->

            val args = backStackEntry.toRoute<TokenDetailRoute>()

            TokenDetailScreen(
                tokenId = args.tokenId,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<BackupRoute> { backStackEntry ->

            val args = backStackEntry.toRoute<BackupRoute>()

            BackupScreen(
                walletId = args.walletId,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<AuthenticateRoute> { backStackEntry ->

            val args = backStackEntry.toRoute<AuthenticateRoute>()

            AuthenticationRequiredScreen(
                canAuthenticate = canAuthenticate,
                onAuthenticated = {
                    navController.navigate(args.targetRoute) {
                        popUpTo<AuthenticateRoute> { inclusive = true }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }
    }
}