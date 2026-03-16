package com.example.nexuswallet.feature.navigation

import com.example.nexuswallet.feature.wallet.domain.model.Network
import kotlinx.serialization.Serializable

@Serializable
sealed class AuthTarget {
    @Serializable
    data class WalletDetail(val walletId: String) : AuthTarget()

    @Serializable
    data class CoinDetail(val walletId: String, val network: Network) : AuthTarget()

    @Serializable
    data class Send(val walletId: String, val network: Network) : AuthTarget()

    @Serializable
    data class Receive(val walletId: String, val network: Network) : AuthTarget()

    @Serializable
    data class TransactionDetail(val walletId: String, val transactionId: String) : AuthTarget()
}