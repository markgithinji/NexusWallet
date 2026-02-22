package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
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
    fun provideGetTransactionUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository
    ): GetTransactionUseCase {
        return GetTransactionUseCase(
           ethereumTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetWalletTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository
    ): GetWalletTransactionsUseCase {
        return GetWalletTransactionsUseCase(
            ethereumTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetPendingTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository
    ): GetPendingTransactionsUseCase {
        return GetPendingTransactionsUseCase(
            ethereumTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideValidateAddressUseCase(): ValidateAddressUseCase {
        return ValidateAddressUseCase()
    }

    @Provides
    @Singleton
    fun provideGetFeeEstimateUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository
    ): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCase(
            ethereumBlockchainRepository
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
    fun provideSendEthereumUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
    ): SendEthereumUseCase {
        return SendEthereumUseCase(
            walletRepository,
            ethereumBlockchainRepository,
            ethereumTransactionRepository,
            securityPreferencesRepository,
            keyStoreRepository
        )
    }

    @Provides
    @Singleton
    fun provideSyncEthereumTransactionsUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        walletRepository: WalletRepository
    ): SyncEthereumTransactionsUseCase {
        return SyncEthereumTransactionsUseCase(
            ethereumBlockchainRepository,
            ethereumTransactionRepository,
            walletRepository
        )
    }
}