package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetUSDCBalanceUseCase(
        walletRepository: WalletRepository,
        usdcBlockchainRepository: USDCBlockchainRepository
    ): GetUSDCBalanceUseCase {
        return GetUSDCBalanceUseCase(walletRepository, usdcBlockchainRepository)
    }

    @Provides
    @Singleton
    fun provideSendUSDCUseCase(
        walletRepository: WalletRepository,
        usdcBlockchainRepository: USDCBlockchainRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        keyManager: KeyManager,
        usdcTransactionRepository: USDCTransactionRepository
    ): SendUSDCUseCase {
        return SendUSDCUseCase(
            walletRepository,
            usdcBlockchainRepository,
            ethereumBlockchainRepository,
            keyManager,
            usdcTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetETHBalanceForGasUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository
    ): GetETHBalanceForGasUseCase {
        return GetETHBalanceForGasUseCase(walletRepository, ethereumBlockchainRepository)
    }
}