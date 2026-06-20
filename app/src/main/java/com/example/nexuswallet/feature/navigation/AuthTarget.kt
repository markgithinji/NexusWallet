package com.example.nexuswallet.feature.navigation

import com.example.nexuswallet.feature.wallet.domain.model.Coin
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthTarget {
    @Serializable
    data class WalletDetail(val walletId: String) : AuthTarget

    @Serializable
    data class CoinDetail(val walletId: String, val coin: Coin) : AuthTarget

    @Serializable
    data class Send(val walletId: String, val coin: Coin) : AuthTarget

    @Serializable
    data class Receive(val walletId: String, val coin: Coin) : AuthTarget

    @Serializable
    data class TransactionDetail(val walletId: String, val transactionId: String, val coin: Coin) :
        AuthTarget

    @Serializable
    data class Backup(val walletId: String) : AuthTarget
}