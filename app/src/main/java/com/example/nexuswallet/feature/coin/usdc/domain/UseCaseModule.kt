package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.GetFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetFeeEstimateUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetPendingTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetPendingTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetWalletTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetWalletTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.ValidateEthereumSendUseCase
import com.example.nexuswallet.feature.coin.ethereum.ValidateEthereumSendUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.data.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
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
    fun provideGetTransactionUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository,
        logger: Logger
    ): GetTransactionUseCase {
        return GetTransactionUseCaseImpl(
            ethereumTransactionRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetWalletTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository,
        logger: Logger
    ): GetWalletTransactionsUseCase {
        return GetWalletTransactionsUseCaseImpl(
            ethereumTransactionRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetPendingTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository,
        logger: Logger
    ): GetPendingTransactionsUseCase {
        return GetPendingTransactionsUseCaseImpl(
            ethereumTransactionRepository,
            logger
        )
    }
}