package com.example.nexuswallet.feature.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.nexuswallet.feature.bitcoin.ui.send.BitcoinSendScreen
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.ethereum.ui.EthereumSendScreen
import com.example.nexuswallet.feature.market.ui.MarketScreen
import com.example.nexuswallet.feature.market.ui.TokenDetailScreen
import com.example.nexuswallet.feature.settings.ui.about.AboutScreen
import com.example.nexuswallet.feature.settings.ui.auth.AuthenticationRequiredScreen
import com.example.nexuswallet.feature.settings.ui.main.SettingsScreen
import com.example.nexuswallet.feature.settings.ui.security.SecuritySettingsScreen
import com.example.nexuswallet.feature.solana.ui.SolanaSendScreen
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.ui.TransactionReviewScreen
import com.example.nexuswallet.feature.wallet.ui.addressbook.AddressBookScreen
import com.example.nexuswallet.feature.wallet.ui.backup.BackupScreen
import com.example.nexuswallet.feature.wallet.ui.backup.BackupViewModel
import com.example.nexuswallet.feature.wallet.ui.coindetail.CoinDetailScreen
import com.example.nexuswallet.feature.wallet.ui.history.TransactionHistoryScreen
import com.example.nexuswallet.feature.wallet.ui.importwallet.ImportWalletScreen
import com.example.nexuswallet.feature.wallet.ui.importwallet.ImportWalletViewModel
import com.example.nexuswallet.feature.wallet.ui.recive.ReceiveScreen
import com.example.nexuswallet.feature.wallet.ui.transactiondetail.TransactionDetailScreen
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationScreen
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WalletCreationViewModel
import com.example.nexuswallet.feature.wallet.ui.walletcreation.WelcomeScreen
import com.example.nexuswallet.feature.wallet.ui.walletdetail.WalletDetailScreen
import kotlin.reflect.KType

@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.onboardingGraph(navController: NavController) {
    composable<WelcomeRoute> {
        WelcomeScreen(
            onCreateWallet = { navController.navigate(CreateWalletRoute) },
            onImportWallet = { navController.navigate(ImportWalletRoute) },
            onSkip = {
                navController.navigate(MainRoute) {
                    popUpTo<WelcomeRoute> { inclusive = true }
                }
            },
            onRestoreSuccess = {
                navController.navigate(MainRoute) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        )
    }

    composable<CreateWalletRoute> {
        val viewModel = hiltViewModel<WalletCreationViewModel>()
        WalletCreationScreen(
            viewModel = viewModel,
            onNavigateUp = { navController.navigateUp() },
            onNavigateToMain = {
                navController.navigate(MainRoute) {
                    popUpTo(navController.graph.id) { inclusive = true }
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
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        )
    }
}

fun NavGraphBuilder.mainGraph(
    navController: NavController,
    isAuthenticationRequired: Boolean
) {
    composable<MainRoute> {
        MainTabScreen(
            onNavigateToCreateWallet = { navController.navigate(CreateWalletRoute) },
            onNavigateToImportWallet = { navController.navigate(ImportWalletRoute) },
            onNavigateToWalletDetail = { walletId ->
                if (isAuthenticationRequired) {
                    navController.navigate(AuthenticateRoute(AuthTarget.WalletDetail(walletId)))
                } else {
                    navController.navigate(WalletDetailRoute(walletId))
                }
            },
            onNavigateToTokenDetail = { tokenId ->
                navController.navigate(TokenDetailRoute(tokenId))
            },
            onNavigateToSecurity = { navController.navigate(SecuritySettingsRoute) },
            onNavigateToAbout = { navController.navigate(AboutRoute) },
            onNavigateToAddressBook = { navController.navigate(AddressBookRoute) },
            padding = PaddingValues(0.dp)
        )
    }

    composable<MarketRoute> {
        MarketScreen(
            onNavigateToTokenDetail = { navController.navigate(TokenDetailRoute(it)) },
            padding = PaddingValues(0.dp)
        )
    }

    composable<TokenDetailRoute> { backStackEntry ->
        val args = backStackEntry.toRoute<TokenDetailRoute>()
        TokenDetailScreen(
            tokenId = args.tokenId,
            onNavigateUp = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.walletGraph(
    navController: NavController,
    typeMap: Map<KType, NavType<*>>,
    isRequireAuthForSendEnabled: Boolean
) {
    composable<WalletDetailRoute>(typeMap = typeMap) { backStackEntry ->
        val args = backStackEntry.toRoute<WalletDetailRoute>()
        WalletDetailScreen(
            walletId = args.walletId,
            onNavigateUp = { navController.navigateUp() },
            onAssetClick = { walletId, coin -> navController.navigate(CoinDetailRoute(walletId, coin)) },
            onReceiveClick = { walletId, coin -> navController.navigate(ReceiveRoute(walletId, coin)) },
            onSendClick = { walletId, coin ->
                if (isRequireAuthForSendEnabled) {
                    navController.navigate(AuthenticateRoute(AuthTarget.Send(walletId, coin)))
                } else {
                    navController.navigate(SendRoute(walletId, coin))
                }
            },
            onNavigateToAllTransactions = { walletId -> navController.navigate(AllTransactionsRoute(walletId)) },
            onNavigateToTransactionDetail = { walletId, txId, coin ->
                navController.navigate(TransactionDetailRoute(walletId, txId, coin))
            },
            onMoreClick = { navController.navigate(BackupRoute(args.walletId)) }
        )
    }

    composable<CoinDetailRoute>(typeMap = typeMap) { backStackEntry ->
        val args = backStackEntry.toRoute<CoinDetailRoute>()
        CoinDetailScreen(
            walletId = args.walletId,
            coin = args.coin,
            onNavigateUp = { navController.navigateUp() },
            onNavigateToReceive = { walletId, coin -> navController.navigate(ReceiveRoute(walletId, coin)) },
            onNavigateToSend = { walletId, coin ->
                if (isRequireAuthForSendEnabled) {
                    navController.navigate(AuthenticateRoute(AuthTarget.Send(walletId, coin)))
                } else {
                    navController.navigate(SendRoute(walletId, coin))
                }
            },
            onNavigateToAllTransactions = { walletId, coin -> navController.navigate(CoinTransactionsRoute(walletId, coin)) },
            onNavigateToTransactionDetail = { walletId, txId, coin ->
                navController.navigate(TransactionDetailRoute(walletId, txId, coin))
            }
        )
    }

    composable<ReceiveRoute>(typeMap = typeMap) { backStackEntry ->
        val args = backStackEntry.toRoute<ReceiveRoute>()
        ReceiveScreen(
            walletId = args.walletId,
            coin = args.coin,
            onNavigateUp = { navController.navigateUp() }
        )
    }

    composable<SendRoute>(typeMap = typeMap) { backStackEntry ->
        val args = backStackEntry.toRoute<SendRoute>()
        when (val coin = args.coin) {
            is BitcoinCoin -> {
                BitcoinSendScreen(
                    walletId = args.walletId,
                    coin = args.coin,
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToReview = { walletId, toAddress, amount, feeLevel, c ->
                        navController.navigate(ReviewRoute(walletId, toAddress, amount, feeLevel?.name, c))
                    }
                )
            }
            is EVMToken -> {
                EthereumSendScreen(
                    walletId = args.walletId,
                    coin = args.coin,
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToReview = { walletId, toAddress, amount, feeLevel, c ->
                        navController.navigate(ReviewRoute(walletId, toAddress, amount, feeLevel?.name, c))
                    }
                )
            }
            is SolanaCoin -> {
                SolanaSendScreen(
                    walletId = args.walletId,
                    coin = coin,
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToReview = { walletId, toAddress, amount, feeLevel, c ->
                        navController.navigate(ReviewRoute(walletId, toAddress, amount, feeLevel?.name, c))
                    }
                )
            }
        }
    }

    composable<ReviewRoute>(typeMap = typeMap) { backStackEntry ->
        val args = backStackEntry.toRoute<ReviewRoute>()
        TransactionReviewScreen(
            walletId = args.walletId,
            toAddress = args.toAddress,
            amount = args.amount,
            feeLevel = args.feeLevel ?: FeeLevel.NORMAL.name,
            coin = args.coin,
            onNavigateUp = { navController.navigateUp() },
            onDone = { walletId, coin ->
                navController.navigate(CoinDetailRoute(walletId, coin)) {
                    popUpTo(CoinDetailRoute(walletId, coin)) { inclusive = true }
                }
            }
        )
    }

    composable<TransactionDetailRoute>(typeMap = typeMap) { backStackEntry ->
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

    composable<BackupRoute> { backStackEntry ->
        val args = backStackEntry.toRoute<BackupRoute>()
        val viewModel = hiltViewModel<BackupViewModel>()
        BackupScreen(
            walletId = args.walletId,
            onNavigateUp = { navController.navigateUp() },
            viewModel = viewModel
        )
    }
}

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateToSecurity = { navController.navigate(SecuritySettingsRoute) },
            onNavigateToAbout = { navController.navigate(AboutRoute) },
            onNavigateToAddressBook = { navController.navigate(AddressBookRoute) }
        )
    }

    composable<AboutRoute> {
        AboutScreen(onNavigateUp = { navController.navigateUp() })
    }

    composable<SecuritySettingsRoute> {
        SecuritySettingsScreen(onNavigateUp = { navController.navigateUp() })
    }

    composable<AddressBookRoute> {
        AddressBookScreen(onNavigateUp = { navController.navigateUp() })
    }
}

fun NavGraphBuilder.authGraph(
    navController: NavController,
    typeMap: Map<KType, NavType<*>>,
    canAuthenticate: Boolean
) {
    composable<AuthenticateRoute>(typeMap = typeMap) { backStackEntry ->
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
            onCancel = { navController.popBackStack() }
        )
    }
}
