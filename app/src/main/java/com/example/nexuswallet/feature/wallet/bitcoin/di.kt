package com.example.nexuswallet.feature.wallet.bitcoin

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.data.test.SepoliaRepository
import com.example.nexuswallet.feature.wallet.data.test.kettest.KeyStorageTestRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BitcoinModule {

    @Provides
    @Singleton
    fun provideBitcoinBlockchainRepository(
    ): BitcoinBlockchainRepository {
        return BitcoinBlockchainRepository()
    }

    @Provides
    @Singleton
    fun provideBitcoinTransactionRepository(
       localDataSource: TransactionLocalDataSource,
       bitcoinBlockchainRepository: BitcoinBlockchainRepository,
       walletRepository: WalletRepository,
       keyManager: KeyManager
    ): BitcoinTransactionRepository {
        return BitcoinTransactionRepository(
            localDataSource,
            bitcoinBlockchainRepository,
            walletRepository,
            keyManager
        )
    }
}