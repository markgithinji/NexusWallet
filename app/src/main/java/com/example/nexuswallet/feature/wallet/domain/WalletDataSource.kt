package com.example.nexuswallet.feature.wallet.domain

import kotlinx.coroutines.flow.Flow

interface WalletDataSource {
    suspend fun saveWallet(wallet: Wallet)
    suspend fun loadWallet(walletId: String): Wallet?
    fun loadAllWallets(): Flow<List<Wallet>>
    suspend fun deleteWallet(walletId: String)
}