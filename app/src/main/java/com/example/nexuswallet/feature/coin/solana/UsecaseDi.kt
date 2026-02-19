package com.example.nexuswallet.feature.coin.solana

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

    @Provides
    @Singleton
    fun provideSendSolanaUseCase(
        walletRepository: WalletRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        keyManager: KeyManager
    ): SendSolanaUseCase {
        return SendSolanaUseCase(
            walletRepository,
            solanaBlockchainRepository,
            solanaTransactionRepository,
            keyManager
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaWalletUseCase(
        walletRepository: WalletRepository
    ): GetSolanaWalletUseCase {
        return GetSolanaWalletUseCase(walletRepository)
    }

    @Provides
    @Singleton
    fun provideSyncSolanaTransactionsUseCase(
        solanaBlockchainRepository: SolanaBlockchainRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        walletRepository: WalletRepository
    ): SyncSolanaTransactionsUseCase {
        return SyncSolanaTransactionsUseCase(
            solanaBlockchainRepository,
            solanaTransactionRepository,
            walletRepository
        )
    }
}