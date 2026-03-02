package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepositoryImpl
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.Web3jFactory
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
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
    fun provideSyncEthereumTransactionsUseCase(
        evmBlockchainRepository: EVMBlockchainRepository,
        evmTransactionRepository: EVMTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncEthereumTransactionsUseCase {
        return SyncEthereumTransactionsUseCaseImpl(
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
        return GetTransactionUseCaseImpl(
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
        return GetWalletTransactionsUseCaseImpl(
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
        return GetPendingTransactionsUseCaseImpl(
            evmTransactionRepository = evmTransactionRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateEVMSendUseCase(
        getFeeEstimateUseCase: GetFeeEstimateUseCase,
        logger: Logger
    ): ValidateEVMSendUseCase {
        return ValidateEVMSendUseCaseImpl(
            getFeeEstimateUseCase = getFeeEstimateUseCase,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetFeeEstimateUseCase(
        evmBlockchainRepository: EVMBlockchainRepository,
        logger: Logger
    ): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCaseImpl(
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
        return GetEthereumWalletUseCaseImpl(
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
        return SendEVMAssetUseCaseImpl(
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
    fun provideEVMTransactionDao(database: WalletDatabase): EVMTransactionDao {
        return database.evmTransactionDao()
    }

    @Provides
    @Singleton
    fun provideEVMTransactionRepository(
        evmTransactionDao: EVMTransactionDao
    ): EVMTransactionRepository {
        return EVMTransactionRepositoryImpl(evmTransactionDao)
    }

    @Provides
    @Singleton
    fun provideEVMBlockchainRepository(
        etherscanApiService: EtherscanApiService,
        web3jFactory: Web3jFactory
    ): EVMBlockchainRepository {
        return EVMBlockchainRepositoryImpl(
            etherscanApi = etherscanApiService,
            web3jFactory = web3jFactory
        )
    }
}