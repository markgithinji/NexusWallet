package com.example.nexuswallet.feature.wallet.domain.datasource

import com.example.nexuswallet.feature.wallet.domain.WalletBalance

interface BalanceDataSource {
    suspend fun saveWalletBalance(balance: WalletBalance)
    suspend fun loadWalletBalance(walletId: String): WalletBalance?
}