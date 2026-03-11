package com.example.nexuswallet.feature.coin.solana.domain.di

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.solana.GetSolanaBalanceUseCase
import com.example.nexuswallet.feature.coin.solana.GetSolanaBalanceUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.GetSolanaFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.solana.GetSolanaFeeEstimateUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.GetSolanaWalletUseCase
import com.example.nexuswallet.feature.coin.solana.GetSolanaWalletUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.SendSolanaUseCase
import com.example.nexuswallet.feature.coin.solana.SendSolanaUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.SyncSolanaTransactionsUseCase
import com.example.nexuswallet.feature.coin.solana.SyncSolanaTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.ValidateSolanaAddressUseCase
import com.example.nexuswallet.feature.coin.solana.ValidateSolanaAddressUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.domain.usecase.ValidateSolanaSendUseCase
import com.example.nexuswallet.feature.coin.solana.ValidateSolanaSendUseCaseImpl
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
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
}