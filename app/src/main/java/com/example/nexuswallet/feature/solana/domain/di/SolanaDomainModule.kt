package com.example.nexuswallet.feature.solana.domain.di

import com.example.nexuswallet.feature.core.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaBalanceUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaFeeEstimateUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaWalletUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.SendSolanaUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.SyncSolanaTransactionsUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.ValidateSolanaAddressUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.ValidateSolanaSendUseCase
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolanaUseDomainModule {

    @Provides
    @Singleton
    fun provideSyncSolanaTransactionsUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncSolanaTransactionsUseCase {
        return SyncSolanaTransactionsUseCase(
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
        return GetSolanaWalletUseCase(
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
        return SendSolanaUseCase(
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
        return GetSolanaBalanceUseCase(
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
        return GetSolanaFeeEstimateUseCase(
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
        return ValidateSolanaAddressUseCase(
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
        return ValidateSolanaSendUseCase(
            validateSolanaAddressUseCase,
            logger
        )
    }
}