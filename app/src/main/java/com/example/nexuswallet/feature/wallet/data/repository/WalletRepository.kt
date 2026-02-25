package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val localDataSource: WalletLocalDataSource
) {

    fun observeWallets(): Flow<List<Wallet>> = localDataSource.loadAllWallets()

    // === WALLET CRUD ===
    suspend fun getWallet(walletId: String): Wallet? {
        return localDataSource.loadWallet(walletId)
    }

    suspend fun deleteWallet(walletId: String) {
        localDataSource.deleteWallet(walletId)
    }

    // === BALANCE OPERATIONS ===
    suspend fun getWalletBalance(walletId: String): WalletBalance? {
        return localDataSource.loadWalletBalance(walletId)
    }
}