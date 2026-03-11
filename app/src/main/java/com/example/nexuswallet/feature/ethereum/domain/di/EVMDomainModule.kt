package com.example.nexuswallet.feature.ethereum.domain.di

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.GetEthereumWalletUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.GetFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.GetPendingTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.GetWalletTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.SendEVMAssetUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.domain.usecase.ValidateEVMSendUseCase
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EVMDomainModule {

    @Provides
    @Singleton
    fun provideSyncEthereumTransactionsUseCase(
        evmBlockchainRepository: EVMBlockchainRepository,
        evmTransactionRepository: EVMTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncEthereumTransactionsUseCase {
        return SyncEthereumTransactionsUseCase(
            evmBlockchainRepository = evmBlockchainRepository,
            evmTransactionRepository = evmTransactionRepository,
            walletRepository = walletRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetTransactionUseCase(
        evmTransactionRepository: EVMTransactionRepository,
        logger: Logger
    ): GetTransactionUseCase {
        return GetTransactionUseCase(
            evmTransactionRepository = evmTransactionRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetWalletTransactionsUseCase(
        evmTransactionRepository: EVMTransactionRepository,
        logger: Logger
    ): GetWalletTransactionsUseCase {
        return GetWalletTransactionsUseCase(
            evmTransactionRepository = evmTransactionRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetPendingTransactionsUseCase(
        evmTransactionRepository: EVMTransactionRepository,
        logger: Logger
    ): GetPendingTransactionsUseCase {
        return GetPendingTransactionsUseCase(
            evmTransactionRepository = evmTransactionRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateEVMSendUseCase(
        evmBlockchainRepository: EVMBlockchainRepository,
        logger: Logger
    ): ValidateEVMSendUseCase {
        return ValidateEVMSendUseCase(
            evmBlockchainRepository = evmBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetEthereumWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetEthereumWalletUseCase {
        return GetEthereumWalletUseCase(
            walletRepository = walletRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideSendEVMAssetUseCase(
        walletRepository: WalletRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        evmTransactionRepository: EVMTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): SendEVMAssetUseCase {
        return SendEVMAssetUseCase(
            walletRepository = walletRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            evmTransactionRepository = evmTransactionRepository,
            securityPreferencesRepository = securityPreferencesRepository,
            keyStoreRepository = keyStoreRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetFeeEstimateUseCase(
        evmBlockchainRepository: EVMBlockchainRepository,
        logger: Logger
    ): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCase(
            evmBlockchainRepository = evmBlockchainRepository,
            logger = logger
        )
    }
}