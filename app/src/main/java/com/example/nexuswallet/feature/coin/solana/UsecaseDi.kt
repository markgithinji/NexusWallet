package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
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
        solanaTransactionRepository: SolanaTransactionRepository,
    ): CreateSolanaTransactionUseCase {
        return CreateSolanaTransactionUseCase(
            walletRepository,
            solanaBlockchainRepository,
            solanaTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideSignSolanaTransactionUseCase(
        walletRepository: WalletRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        keyManager: KeyManager,
        solanaTransactionRepository: SolanaTransactionRepository,
    ): SignSolanaTransactionUseCase {
        return SignSolanaTransactionUseCase(
            walletRepository,
            solanaBlockchainRepository,
            keyManager,
            solanaTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideBroadcastSolanaTransactionUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
    ): BroadcastSolanaTransactionUseCase {
        return BroadcastSolanaTransactionUseCase(
            solanaBlockchainRepository,
            solanaTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaBalanceUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository
    ): GetSolanaBalanceUseCase {
        return GetSolanaBalanceUseCase(solanaBlockchainRepository)
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
    fun provideRequestSolanaAirdropUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository
    ): RequestSolanaAirdropUseCase {
        return RequestSolanaAirdropUseCase(solanaBlockchainRepository)
    }

//    @Provides
//    @Singleton
//    fun provideGetSolanaTransactionHistoryUseCase(
//        solanaBlockchainRepository: SolanaBlockchainRepository
//    ): GetSolanaTransactionHistoryUseCase {
//        return GetSolanaTransactionHistoryUseCase(solanaBlockchainRepository)
//    }

    @Provides
    @Singleton
    fun provideValidateSolanaAddressUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository
    ): ValidateSolanaAddressUseCase {
        return ValidateSolanaAddressUseCase(solanaBlockchainRepository)
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionDao(database: WalletDatabase): SolanaTransactionDao {
        return database.solanaTransactionDao()
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionRepository(
        solanaTransactionDao: SolanaTransactionDao
    ): SolanaTransactionRepository {
        return SolanaTransactionRepository(solanaTransactionDao)
    }
}