package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
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
    fun provideGetBitcoinFeeEstimateUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository
    ): GetBitcoinFeeEstimateUseCase {
        return GetBitcoinFeeEstimateUseCase(bitcoinBlockchainRepository)
    }

    @Provides
    @Singleton
    fun provideGetBitcoinBalanceUseCase(
        bitcoinBlockchainRepository: BitcoinBlockchainRepository
    ): GetBitcoinBalanceUseCase {
        return GetBitcoinBalanceUseCase(bitcoinBlockchainRepository)
    }

    @Provides
    @Singleton
    fun provideValidateBitcoinAddressUseCase(): ValidateBitcoinAddressUseCase {
        return ValidateBitcoinAddressUseCase()
    }

    @Provides
    @Singleton
    fun provideSendBitcoinUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
    ): SendBitcoinUseCase {
        return SendBitcoinUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            bitcoinTransactionRepository,
            keyStoreRepository,
            securityPreferencesRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetBitcoinWalletUseCase(
        walletRepository: WalletRepository,
    ): GetBitcoinWalletUseCase {
        return GetBitcoinWalletUseCase(
            walletRepository,
        )
    }
}