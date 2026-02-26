package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EthereumBlockchainRepositoryImpl
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
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
    fun provideSyncEthereumTransactionsUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncEthereumTransactionsUseCase {
        return SyncEthereumTransactionsUseCaseImpl(
            ethereumBlockchainRepository,
            ethereumTransactionRepository,
            walletRepository,
            logger
        )
    }

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

    @Provides
    @Singleton
    fun provideValidateEthereumSendUseCase(
        getFeeEstimateUseCase: GetFeeEstimateUseCase,
        logger: Logger
    ): ValidateEthereumSendUseCase {
        return ValidateEthereumSendUseCaseImpl(
            getFeeEstimateUseCase,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetFeeEstimateUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        logger: Logger
    ): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCaseImpl(
            ethereumBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetEthereumWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetEthereumWalletUseCase {
        return GetEthereumWalletUseCaseImpl(
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSendEthereumUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): SendEthereumUseCase {
        return SendEthereumUseCaseImpl(
            walletRepository,
            ethereumBlockchainRepository,
            ethereumTransactionRepository,
            securityPreferencesRepository,
            keyStoreRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideEthereumTransactionDao(database: WalletDatabase): EthereumTransactionDao {
        return database.ethereumTransactionDao()
    }

    @Provides
    @Singleton
    fun provideEthereumTransactionRepository(
        ethereumTransactionDao: EthereumTransactionDao
    ): EthereumTransactionRepository {
        return EthereumTransactionRepository(ethereumTransactionDao)
    }

    @Provides
    @Singleton
    fun provideGetEthereumBalanceUseCase(
        etherscanApiService :EtherscanApiService
    ): EthereumBlockchainRepository {
        return EthereumBlockchainRepositoryImpl(
            etherscanApiService
        )
    }
}