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
object SolanaModule {

    @Provides
    @Singleton
    fun provideSolanaBlockchainRepository(
    ): SolanaBlockchainRepository {
        return SolanaBlockchainRepository()
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionRepository(
      localDataSource: TransactionLocalDataSource,
      solanaBlockchainRepository: SolanaBlockchainRepository,
      walletRepository: WalletRepository,
      keyManager: KeyManager
    ): SolanaTransactionRepository {
        return SolanaTransactionRepository(
            localDataSource,
            solanaBlockchainRepository,
            walletRepository,
            keyManager
        )
    }
}