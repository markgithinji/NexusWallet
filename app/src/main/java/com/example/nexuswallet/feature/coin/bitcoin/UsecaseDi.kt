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
        bitcoinTransactionRepository: BitcoinTransactionRepository
    ): CreateBitcoinTransactionUseCase {
        return CreateBitcoinTransactionUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            bitcoinTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideSignBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        keyManager: KeyManager,
        bitcoinTransactionRepository: BitcoinTransactionRepository
    ): SignBitcoinTransactionUseCase {
        return SignBitcoinTransactionUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            keyManager,
            bitcoinTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideBroadcastBitcoinTransactionUseCase(
        walletRepository: WalletRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository

    ): BroadcastBitcoinTransactionUseCase {
        return BroadcastBitcoinTransactionUseCase(
            walletRepository,
            bitcoinBlockchainRepository,
            bitcoinTransactionRepository
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
    @Provides
    @Singleton
    fun provideSendBitcoinUseCase(
        createBitcoinTransactionUseCase: CreateBitcoinTransactionUseCase,
        signBitcoinTransactionUseCase: SignBitcoinTransactionUseCase,
        broadcastBitcoinTransactionUseCase: BroadcastBitcoinTransactionUseCase,
        walletRepository: WalletRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository
    ): SendBitcoinUseCase {
        return SendBitcoinUseCase(
            createBitcoinTransactionUseCase = createBitcoinTransactionUseCase,
            signBitcoinTransactionUseCase = signBitcoinTransactionUseCase,
            broadcastBitcoinTransactionUseCase = broadcastBitcoinTransactionUseCase,
            walletRepository = walletRepository,
            bitcoinTransactionRepository = bitcoinTransactionRepository
        )
    }

}