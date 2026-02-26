package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
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
object SolanaUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncSolanaTransactionsUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncSolanaTransactionsUseCase {
        return SyncSolanaTransactionsUseCaseImpl(
            solanaBlockchainRepository,
            solanaTransactionRepository,
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetSolanaWalletUseCase {
        return GetSolanaWalletUseCaseImpl(
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSendSolanaUseCase(
        walletRepository: WalletRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): SendSolanaUseCase {
        return SendSolanaUseCaseImpl(
            walletRepository,
            solanaBlockchainRepository,
            solanaTransactionRepository,
            securityPreferencesRepository,
            keyStoreRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaBalanceUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): GetSolanaBalanceUseCase {
        return GetSolanaBalanceUseCaseImpl(
            solanaBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaFeeEstimateUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): GetSolanaFeeEstimateUseCase {
        return GetSolanaFeeEstimateUseCaseImpl(
            solanaBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateSolanaAddressUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): ValidateSolanaAddressUseCase {
        return ValidateSolanaAddressUseCaseImpl(
            solanaBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateSolanaSendUseCase(
        validateSolanaAddressUseCase: ValidateSolanaAddressUseCase,
        logger: Logger
    ): ValidateSolanaSendUseCase {
        return ValidateSolanaSendUseCaseImpl(
            validateSolanaAddressUseCase,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionDao(database: WalletDatabase): SolanaTransactionDao {
        return database.solanaTransactionDao()
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionRepository(
        solanaTransactionDao: SolanaTransactionDao
    ): SolanaTransactionRepository {
        return SolanaTransactionRepository(solanaTransactionDao)
    }
}