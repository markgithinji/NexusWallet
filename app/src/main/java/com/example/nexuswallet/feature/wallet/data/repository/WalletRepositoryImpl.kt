package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val walletDataSource: WalletDataSource,
    private val balanceDataSource: BalanceDataSource,
    private val walletDatabase: WalletDatabase
) : WalletRepository {

    override fun observeWallets(): Flow<List<Wallet>> =
        walletDataSource.loadAllWallets()

    // === WALLET CRUD ===
    override suspend fun getWallet(walletId: String): Wallet? =
        walletDataSource.loadWallet(walletId)

    override suspend fun deleteWallet(walletId: String) =
        walletDataSource.deleteWallet(walletId)

    // === BALANCE OPERATIONS ===
    override suspend fun getWalletBalance(walletId: String): WalletBalance? =
        balanceDataSource.loadWalletBalance(walletId)

    override suspend fun saveWalletBalance(balance: WalletBalance) =
        balanceDataSource.saveWalletBalance(balance)

    override fun observeWalletBalance(walletId: String): Flow<WalletBalance?> =
        balanceDataSource.observeWalletBalance(walletId)

    override fun observeAllBalances(): Flow<Map<String, WalletBalance>> =
        balanceDataSource.observeAllBalances()

    override suspend fun updateWalletName(walletId: String, newName: String) {
        walletDataSource.updateWalletName(walletId, newName)
    }

    override suspend fun clearAllData() {
        walletDatabase.clearAllTables()
    }
}