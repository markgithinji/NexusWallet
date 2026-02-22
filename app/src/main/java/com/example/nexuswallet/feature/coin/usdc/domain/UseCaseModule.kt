package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
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
        usdcTransactionRepository: USDCTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
    ): SendUSDCUseCase {
        return SendUSDCUseCase(
            walletRepository,
            usdcBlockchainRepository,
            usdcTransactionRepository,
            securityPreferencesRepository,
            keyStoreRepository
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

    @Provides
    @Singleton
    fun provideGetUSDCFeeEstimateUseCase(
        blockchainRepository: USDCBlockchainRepository
    ): GetUSDCFeeEstimateUseCase {
        return GetUSDCFeeEstimateUseCase(blockchainRepository )
    }
}