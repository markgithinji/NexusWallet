package com.example.nexuswallet.feature.solana.domain.di

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaBalanceUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaFeeEstimateUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.GetSolanaWalletUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.SendSolanaUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.ValidateSolanaAddressUseCase
import com.example.nexuswallet.feature.solana.domain.usecase.ValidateSolanaSendUseCase
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
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
    fun provideGetSolanaWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger,
    ): GetSolanaWalletUseCase {
        return GetSolanaWalletUseCase(
            walletRepository = walletRepository,
            logger = logger
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
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        logger: Logger
    ): SendSolanaUseCase {
        return SendSolanaUseCase(
            walletRepository = walletRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            solanaTransactionRepository = solanaTransactionRepository,
            securityPreferencesRepository = securityPreferencesRepository,
            keyStoreRepository = keyStoreRepository,
            logger = logger,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaBalanceUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): GetSolanaBalanceUseCase {
        return GetSolanaBalanceUseCase(
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaFeeEstimateUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): GetSolanaFeeEstimateUseCase {
        return GetSolanaFeeEstimateUseCase(
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateSolanaAddressUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): ValidateSolanaAddressUseCase {
        return ValidateSolanaAddressUseCase(
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateSolanaSendUseCase(
        logger: Logger
    ): ValidateSolanaSendUseCase {
        return ValidateSolanaSendUseCase(
            logger = logger
        )
    }
}