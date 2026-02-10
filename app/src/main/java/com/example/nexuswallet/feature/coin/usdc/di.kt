package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.coin.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
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
    fun provideUSDCBlockchainRepository(
        etherscanApi: EtherscanApiService,
        ethereumBlockchainRepository: EthereumBlockchainRepository
    ): USDCBlockchainRepository {
        return USDCBlockchainRepository(
            etherscanApi = etherscanApi,
            ethereumBlockchainRepository = ethereumBlockchainRepository
        )

    }

    @Provides
    @Singleton
    fun provideUSDCTransactionRepository(
        localDataSource: TransactionLocalDataSource,
        usdcBlockchainRepository: USDCBlockchainRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        keyManager: KeyManager,
        walletRepository: WalletRepository
    ): USDCTransactionRepository {
        return USDCTransactionRepository(
            localDataSource = localDataSource,
            usdcBlockchainRepository = usdcBlockchainRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            keyManager = keyManager,
            walletRepository = walletRepository
        )
    }
}