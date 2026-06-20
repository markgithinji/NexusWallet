package com.example.nexuswallet.feature.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.nexuswallet.feature.authentication.ui.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.bitcoin.ui.send.BitcoinSendScreen
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.ethereum.ui.EthereumSendScreen
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.navigation.navtype.AuthTargetNavType
import com.example.nexuswallet.feature.navigation.navtype.CoinNavType
import com.example.nexuswallet.feature.navigation.navtype.NetworkNavType
import com.example.nexuswallet.feature.settings.ui.SecuritySettingsScreen
import com.example.nexuswallet.feature.settings.ui.SettingsScreen
import com.example.nexuswallet.feature.solana.ui.SolanaSendScreen
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen
import com.example.nexuswallet.feature.wallet.ui.coindetail.CoinDetailScreen
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.history.TransactionHistoryScreen
import com.example.nexuswallet.feature.wallet.ui.recive.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.transactiondetail.TransactionDetailScreen
import com.example.nexuswallet.feature.wallet.ui.backup.BackupScreen
import com.example.nexuswallet.feature.wallet.ui.backup.BackupViewModel
import com.example.nexuswallet.feature.wallet.ui.importwallet.ImportWalletScreen
import com.example.nexuswallet.feature.wallet.ui.importwallet.ImportWalletViewModel
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WelcomeScreen
import com.example.nexuswallet.feature.wallet.ui.walletdetail.WalletDetailScreen
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
    val isPrivacyModeEnabled by navigationViewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()
    val isRequireAuthForSendEnabled by navigationViewModel.isRequireAuthForSendEnabled.collectAsStateWithLifecycle()

    if (isWalletsLoading) {
        FullScreenLoading(message = "Loading wallets...")
        return
    }

    val startDestination = if (wallets.isNotEmpty()) {
        MainRoute
    } else {
        WelcomeRoute
    }

    val typeMap = remember {
        mapOf(
            typeOf<Coin>() to CoinNavType,
            typeOf<AuthTarget>() to AuthTargetNavType,
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
                    navController.navigate(ImportWalletRoute)
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
                onNavigateToImportWallet = {
                    navController.navigate(ImportWalletRoute)
                },
                onNavigateToWalletDetail = { walletId ->
                    if (isAuthenticationRequired) {
                        navController.navigate(
                            AuthenticateRoute(
                                target = AuthTarget.WalletDetail(walletId)
                            )
                        )
                    } else {
                        navController.navigate(WalletDetailRoute(walletId))
                    }
                },
                onNavigateToTokenDetail = { tokenId ->
                    navController.navigate(TokenDetailRoute(tokenId))
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
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable<ImportWalletRoute> {
            val viewModel = hiltViewModel<ImportWalletViewModel>()

            ImportWalletScreen(
                viewModel = viewModel,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToMain = {
                    navController.navigate(MainRoute) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
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

        composable<WalletDetailRoute>(typeMap = typeMap){ backStackEntry ->
            val args = backStackEntry.toRoute<WalletDetailRoute>()

            WalletDetailScreen(
                walletId = args.walletId,
                onNavigateUp = { navController.navigateUp() },
                onAssetClick = { walletId, coin ->
                    navController.navigate(CoinDetailRoute(walletId, coin))
                },
                onReceiveClick = { walletId, coin ->
                    navController.navigate(ReceiveRoute(walletId, coin))
                },
                onSendClick = { walletId, coin ->
                    if (isRequireAuthForSendEnabled) {
                        navController.navigate(AuthenticateRoute(AuthTarget.Send(walletId, coin)))
                    } else {
                        navController.navigate(SendRoute(walletId, coin))
                    }
                },
                onNavigateToAllTransactions = { walletId ->
                    navController.navigate(AllTransactionsRoute(walletId))
                },
                onNavigateToTransactionDetail = { walletId, txId, coin ->
                    navController.navigate(TransactionDetailRoute(walletId, txId,coin))
                },
                onMoreClick = {
                    navController.navigate(AuthenticateRoute(AuthTarget.Backup(args.walletId)))
                }
            )
        }

        composable<CoinDetailRoute>(
            typeMap = typeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<CoinDetailRoute>()

            CoinDetailScreen(
                walletId = args.walletId,
                coin = args.coin,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToReceive = { walletId, coin ->
                    navController.navigate(ReceiveRoute(walletId, coin))
                },
                onNavigateToSend = { walletId, coin ->
                    if (isRequireAuthForSendEnabled) {
                        navController.navigate(AuthenticateRoute(AuthTarget.Send(walletId, coin)))
                    } else {
                        navController.navigate(SendRoute(walletId, coin))
                    }
                },
                onNavigateToAllTransactions = { walletId, coin ->
                    navController.navigate(CoinTransactionsRoute(walletId, coin))
                },
                onNavigateToTransactionDetail = { walletId, txId, coin ->
                    navController.navigate(TransactionDetailRoute(walletId, txId,coin))
                }
            )
        }

        composable<TransactionDetailRoute>(
            typeMap = typeMap
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionDetailRoute>()

            TransactionDetailScreen(
                walletId = args.walletId,
                transactionId = args.transactionId,
                coin = args.coin,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<AllTransactionsRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<AllTransactionsRoute>()
            TransactionHistoryScreen(
                walletId = args.walletId,
                onNavigateUp = { navController.navigateUp() },
                onTransactionClick = { txId, coin ->
                    navController.navigate(TransactionDetailRoute(args.walletId, txId, coin))
                }
            )
        }

        composable<CoinTransactionsRoute>(typeMap = typeMap) { backStackEntry ->
            val args = backStackEntry.toRoute<CoinTransactionsRoute>()
            TransactionHistoryScreen(
                walletId = args.walletId,
                coin = args.coin,
                onNavigateUp = { navController.navigateUp() },
                onTransactionClick = { txId, coin ->
                    navController.navigate(TransactionDetailRoute(args.walletId, txId, coin))
                }
            )
        }

        composable<ReceiveRoute>(
            typeMap = typeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<ReceiveRoute>()

            ReceiveScreen(
                walletId = args.walletId,
                coin = args.coin,
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<SendRoute>(
            typeMap = typeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<SendRoute>()

            when (val coin = args.coin) {
                is BitcoinCoin -> {
                    BitcoinSendScreen(
                        walletId = args.walletId,
                        coin = args.coin,
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, coin ->
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    coin = coin
                                )
                            )
                        }
                    )
                }

                is EVMToken -> {
                    EthereumSendScreen(
                        walletId = args.walletId,
                        coin = args.coin,
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, coin ->
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    coin = coin
                                )
                            )
                        }
                    )
                }

                is SolanaCoin -> {
                    SolanaSendScreen(
                        walletId = args.walletId,
                        coin = coin,
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToReview = { walletId, toAddress, amount, feeLevel, coin ->
                            navController.navigate(
                                ReviewRoute(
                                    walletId = walletId,
                                    toAddress = toAddress,
                                    amount = amount,
                                    feeLevel = feeLevel?.name,
                                    coin = coin
                                )
                            )
                        }
                    )
                }
            }
        }

        composable<ReviewRoute>(
            typeMap = typeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<ReviewRoute>()
            val feeLevel = args.feeLevel?.let { FeeLevel.valueOf(it) }

            TransactionReviewScreen(
                walletId = args.walletId,
                toAddress = args.toAddress,
                amount = args.amount,
                feeLevel = feeLevel.toString(),
                coin = args.coin,
                onNavigateUp = {
                    navController.navigateUp()
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
            val viewModel = hiltViewModel<BackupViewModel>()

            BackupScreen(
                walletId = args.walletId,
                onNavigateUp = { navController.navigateUp() },
                viewModel = viewModel
            )
        }

        composable<AuthenticateRoute>(
            typeMap = typeMap
        ) { backStackEntry ->

            val args = backStackEntry.toRoute<AuthenticateRoute>()

            AuthenticationRequiredScreen(
                canAuthenticate = canAuthenticate,
                onAuthenticated = {
                    when (val target = args.target) {
                        is AuthTarget.WalletDetail -> {
                            navController.navigate(WalletDetailRoute(target.walletId)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        is AuthTarget.CoinDetail -> {
                            navController.navigate(CoinDetailRoute(target.walletId, target.coin)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        is AuthTarget.Send -> {
                            navController.navigate(SendRoute(target.walletId, target.coin)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        is AuthTarget.Receive -> {
                            navController.navigate(ReceiveRoute(target.walletId, target.coin)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        is AuthTarget.TransactionDetail -> {
                            navController.navigate(TransactionDetailRoute(target.walletId, target.transactionId, target.coin)) {
                                popUpTo<AuthenticateRoute> { inclusive = true }
                            }
                        }
                        is AuthTarget.Backup -> {
                            navController.navigate(BackupRoute(target.walletId)) {
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