package com.example.nexuswallet.feature.wallet.domain.repository

import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun observeWallets(): Flow<List<Wallet>>
    suspend fun getWallet(walletId: String): Wallet?
    suspend fun deleteWallet(walletId: String)
    suspend fun getWalletBalance(walletId: String): WalletBalance?
    suspend fun saveWalletBalance(balance: WalletBalance)
    fun observeWalletBalance(walletId: String): Flow<WalletBalance?>
    fun observeAllBalances(): Flow<Map<String, WalletBalance>>
    suspend fun updateWalletName(walletId: String, newName: String)
    suspend fun saveWallet(wallet: Wallet)
    suspend fun clearAllData()
}