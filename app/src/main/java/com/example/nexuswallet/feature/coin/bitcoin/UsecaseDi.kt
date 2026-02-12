package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
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
    fun provideCreateBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        transactionLocalDataSource: TransactionLocalDataSource
    ): CreateBitcoinTransactionUseCase {
        return CreateBitcoinTransactionUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideSignBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        keyManager: KeyManager,
        transactionLocalDataSource: TransactionLocalDataSource
    ): SignBitcoinTransactionUseCase {
        return SignBitcoinTransactionUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            keyManager,
            transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideBroadcastBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        transactionLocalDataSource: TransactionLocalDataSource
    ): BroadcastBitcoinTransactionUseCase {
        return BroadcastBitcoinTransactionUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            transactionLocalDataSource
        )
    }

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
}