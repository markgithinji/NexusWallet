package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.flow.Flow

interface WalletLocalDataSource {
    suspend fun saveWallet(wallet: Wallet)
    suspend fun loadWallet(walletId: String): Wallet?
    fun loadAllWallets(): Flow<List<Wallet>>
    suspend fun deleteWallet(walletId: String)
    suspend fun saveWalletBalance(balance: WalletBalance)
    suspend fun loadWalletBalance(walletId: String): WalletBalance?
}