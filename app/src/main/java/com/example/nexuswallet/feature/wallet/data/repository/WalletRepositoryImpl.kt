package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.domain.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val walletDataSource: WalletDataSource,
    private val balanceDataSource: BalanceDataSource
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
}