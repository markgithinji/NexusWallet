package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
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
        return SyncBitcoinTransactionsUseCaseImpl(
            bitcoinBlockchainRepository,
            bitcoinTransactionRepository,
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetBitcoinWalletUseCase {
        return GetBitcoinWalletUseCaseImpl(
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
        return SendBitcoinUseCaseImpl(
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
        return GetBitcoinFeeEstimateUseCaseImpl(
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
        return GetBitcoinBalanceUseCaseImpl(
            bitcoinBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateBitcoinAddressUseCase(
        logger: Logger
    ): ValidateBitcoinAddressUseCase {
        return ValidateBitcoinAddressUseCaseImpl(logger)
    }

    @Provides
    @Singleton
    fun provideValidateBitcoinTransactionUseCase(
        logger: Logger,
        validateBitcoinAddressUseCase: ValidateBitcoinAddressUseCase
    ): ValidateBitcoinTransactionUseCase {
        return ValidateBitcoinTransactionUseCaseImpl(validateBitcoinAddressUseCase,logger)
    }
}