package com.example.nexuswallet.feature.wallet.domain

interface BalanceDataSource {
    suspend fun saveWalletBalance(balance: WalletBalance)
    suspend fun loadWalletBalance(walletId: String): WalletBalance?
}