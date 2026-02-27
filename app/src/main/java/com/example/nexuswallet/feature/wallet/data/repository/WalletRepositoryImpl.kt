package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val localDataSource: WalletLocalDataSource
) : WalletRepository {

    override fun observeWallets(): Flow<List<Wallet>> = localDataSource.loadAllWallets()

    // === WALLET CRUD ===
    override suspend fun getWallet(walletId: String): Wallet? {
        return localDataSource.loadWallet(walletId)
    }

    override suspend fun deleteWallet(walletId: String) {
        localDataSource.deleteWallet(walletId)
    }

    // === BALANCE OPERATIONS ===
    override suspend fun getWalletBalance(walletId: String): WalletBalance? {
        return localDataSource.loadWalletBalance(walletId)
    }
}