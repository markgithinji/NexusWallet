package com.example.nexuswallet.feature.coin.solana

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
object SolanaUseCaseModule {

    @Provides
    @Singleton
    fun provideCreateSolanaTransactionUseCase(
        walletRepository: WalletRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        transactionLocalDataSource: TransactionLocalDataSource
    ): CreateSolanaTransactionUseCase {
        return CreateSolanaTransactionUseCase(
            walletRepository,
            solanaBlockchainRepository,
            transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideSignSolanaTransactionUseCase(
        walletRepository: WalletRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        keyManager: KeyManager,
        transactionLocalDataSource: TransactionLocalDataSource
    ): SignSolanaTransactionUseCase {
        return SignSolanaTransactionUseCase(
            walletRepository,
            solanaBlockchainRepository,
            keyManager,
            transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideBroadcastSolanaTransactionUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        transactionLocalDataSource: TransactionLocalDataSource
    ): BroadcastSolanaTransactionUseCase {
        return BroadcastSolanaTransactionUseCase(
            solanaBlockchainRepository,
            transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaFeeEstimateUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository
    ): GetSolanaFeeEstimateUseCase {
        return GetSolanaFeeEstimateUseCase(solanaBlockchainRepository)
    }

    @Provides
    @Singleton
    fun provideGetSolanaRecentBlockhashUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository
    ): GetSolanaRecentBlockhashUseCase {
        return GetSolanaRecentBlockhashUseCase(solanaBlockchainRepository)
    }

    @Provides
    @Singleton
    fun provideValidateSolanaAddressUseCase(): ValidateSolanaAddressUseCase {
        return ValidateSolanaAddressUseCase()
    }
}