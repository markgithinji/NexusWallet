package com.example.nexuswallet

import com.example.nexuswallet.feature.coin.CoinType
import kotlinx.serialization.Serializable

@Serializable
object WelcomeRoute

@Serializable
object MainRoute

@Serializable
object MarketRoute

@Serializable
object CreateWalletRoute

@Serializable
object SettingsRoute

@Serializable
object SecuritySettingsRoute

@Serializable
data class WalletDetailRoute(
    val walletId: String
)

@Serializable
data class CoinDetailRoute(
    val walletId: String,
    val coinType: CoinType
)

@Serializable
data class ReceiveRoute(
    val walletId: String,
    val coinType: CoinType = CoinType.BITCOIN
)

@Serializable
data class SendRoute(
    val walletId: String,
    val coinType: CoinType
)

@Serializable
data class ReviewRoute(
    val walletId: String,
    val coinType: CoinType,
    val toAddress: String,
    val amount: String,
    val feeLevel: String? = null
)

@Serializable
data class TokenDetailRoute(
    val tokenId: String
)

@Serializable
data class AuthenticateRoute(
    val screen: String,
    val walletId: String
)

@Serializable
data class BackupRoute(
    val walletId: String
)