package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.GetBitcoinBalanceUseCase
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.GetBitcoinFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.GetBitcoinWalletUseCase
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.SendBitcoinUseCase
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.SyncBitcoinTransactionsUseCase
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.ValidateBitcoinAddressUseCase
import com.example.nexuswallet.feature.coin.bitcoin.domain.usecase.ValidateBitcoinTransactionUseCase
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BitcoinUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncBitcoinTransactionsUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncBitcoinTransactionsUseCase {
        return SyncBitcoinTransactionsUseCase(
            bitcoinBlockchainRepository,
            bitcoinTransactionRepository,
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun providePrepareBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): PrepareBitcoinTransactionUseCase {
        return PrepareBitcoinTransactionUseCaseImpl(
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
    fun provideGetBitcoinWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetBitcoinWalletUseCase {
        return GetBitcoinWalletUseCase(
            walletRepository,
            logger
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
            walletRepository,
            bitcoinBlockchainRepository,
            bitcoinTransactionRepository,
            keyStoreRepository,
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinFeeEstimateUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        logger: Logger
    ): GetBitcoinFeeEstimateUseCase {
        return GetBitcoinFeeEstimateUseCase(
            bitcoinBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinBalanceUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        logger: Logger
    ): GetBitcoinBalanceUseCase {
        return GetBitcoinBalanceUseCase(
            bitcoinBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateBitcoinAddressUseCase(
        logger: Logger
    ): ValidateBitcoinAddressUseCase {
        return ValidateBitcoinAddressUseCase(logger)
    }

    @Provides
    @Singleton
    fun provideValidateBitcoinTransactionUseCase(
        logger: Logger,
        validateBitcoinAddressUseCase: ValidateBitcoinAddressUseCase
    ): ValidateBitcoinTransactionUseCase {
        return ValidateBitcoinTransactionUseCase(validateBitcoinAddressUseCase,logger)
    }
}