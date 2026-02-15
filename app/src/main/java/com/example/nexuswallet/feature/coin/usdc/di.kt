package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.coin.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class USDCModule {

    @Provides
    @Singleton
    fun provideWeb3jFactory(): Web3jFactory = Web3jFactory()

    @Provides
    @Singleton
    fun provideUSDCBlockchainRepository(
        etherscanApi: EtherscanApiService,
        web3jFactory: Web3jFactory
    ): USDCBlockchainRepository {
        return USDCBlockchainRepository(etherscanApi, web3jFactory)
    }

    @Provides
    @Singleton
    fun provideUSDCTransactionDao(database: WalletDatabase): USDCTransactionDao {
        return database.usdcTransactionDao()
    }

    @Provides
    @Singleton
    fun provideUSDCTransactionRepository(
        usdcTransactionDao: USDCTransactionDao
    ): USDCTransactionRepository {
        return USDCTransactionRepository(usdcTransactionDao)
    }
}