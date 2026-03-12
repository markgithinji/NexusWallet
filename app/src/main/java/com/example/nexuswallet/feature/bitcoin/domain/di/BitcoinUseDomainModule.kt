package com.example.nexuswallet.feature.bitcoin.domain.di

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinBalanceUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinFeeEstimateUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.GetBitcoinWalletUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.PrepareBitcoinTransactionUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.SendBitcoinUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.SyncBitcoinTransactionsUseCase
import com.example.nexuswallet.feature.bitcoin.domain.usecase.ValidateBitcoinTransactionUseCase
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BitcoinUseDomainModule {

    @Provides
    @Singleton
    fun provideSyncBitcoinTransactionsUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncBitcoinTransactionsUseCase {
        return SyncBitcoinTransactionsUseCase(
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            bitcoinTransactionRepository = bitcoinTransactionRepository,
            walletRepository = walletRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun providePrepareBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        logger: Logger
    ): PrepareBitcoinTransactionUseCase {
        return PrepareBitcoinTransactionUseCase(
            walletRepository = walletRepository,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetBitcoinWalletUseCase {
        return GetBitcoinWalletUseCase(
            walletRepository = walletRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideSendBitcoinUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): SendBitcoinUseCase {
        return SendBitcoinUseCase(
            walletRepository = walletRepository,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            bitcoinTransactionRepository = bitcoinTransactionRepository,
            keyStoreRepository = keyStoreRepository,
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinFeeEstimateUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        logger: Logger
    ): GetBitcoinFeeEstimateUseCase {
        return GetBitcoinFeeEstimateUseCase(
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinBalanceUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        logger: Logger
    ): GetBitcoinBalanceUseCase {
        return GetBitcoinBalanceUseCase(
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateBitcoinTransactionUseCase(
        logger: Logger
    ): ValidateBitcoinTransactionUseCase {
        return ValidateBitcoinTransactionUseCase(
            logger = logger
        )
    }
}