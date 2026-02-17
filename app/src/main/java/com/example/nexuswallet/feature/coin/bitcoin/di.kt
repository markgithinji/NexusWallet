package com.example.nexuswallet.feature.coin.bitcoin

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
object BitcoinModule {

    @Provides
    @Singleton
    fun provideBitcoinTransactionDao(database: WalletDatabase): BitcoinTransactionDao {
        return database.bitcoinTransactionDao()
    }

    @Provides
    @Singleton
    fun provideBitcoinTransactionRepository(
        bitcoinTransactionDao: BitcoinTransactionDao
    ): BitcoinTransactionRepository {
        return BitcoinTransactionRepository(bitcoinTransactionDao)
    }
}