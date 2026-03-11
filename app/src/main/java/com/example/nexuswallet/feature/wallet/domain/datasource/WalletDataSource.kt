package com.example.nexuswallet.feature.wallet.domain.datasource

import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import kotlinx.coroutines.flow.Flow

interface WalletDataSource {
    suspend fun saveWallet(wallet: Wallet)
    suspend fun loadWallet(walletId: String): Wallet?
    fun loadAllWallets(): Flow<List<Wallet>>
    suspend fun deleteWallet(walletId: String)
}