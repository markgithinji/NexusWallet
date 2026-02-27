package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun observeWallets(): Flow<List<Wallet>>
    suspend fun getWallet(walletId: String): Wallet?
    suspend fun deleteWallet(walletId: String)
    suspend fun getWalletBalance(walletId: String): WalletBalance?
}