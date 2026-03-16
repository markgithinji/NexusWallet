package com.example.nexuswallet.feature.navigation

import com.example.nexuswallet.feature.wallet.domain.model.Network
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route

@Serializable
data object WelcomeRoute : Route

@Serializable
data object MainRoute : Route

@Serializable
data object MarketRoute : Route

@Serializable
data object CreateWalletRoute : Route

@Serializable
data object SettingsRoute : Route

@Serializable
data object SecuritySettingsRoute : Route

@Serializable
data class WalletDetailRoute(val walletId: String) : Route

@Serializable
data class CoinDetailRoute(val walletId: String, val network: Network) : Route

@Serializable
data class ReceiveRoute(val walletId: String, val network: Network) : Route

@Serializable
data class SendRoute(val walletId: String, val network: Network) : Route

@Serializable
data class ReviewRoute(
    val walletId: String,
    val toAddress: String,
    val amount: String,
    val feeLevel: String? = null,
    val network: Network
) : Route

@Serializable
data class TokenDetailRoute(val tokenId: String) : Route

@Serializable
data class BackupRoute(val walletId: String) : Route

@Serializable
data class TransactionDetailRoute(val walletId: String, val transactionId: String) : Route

@Serializable
data class AuthenticateRoute(
    val targetRoute: String
)