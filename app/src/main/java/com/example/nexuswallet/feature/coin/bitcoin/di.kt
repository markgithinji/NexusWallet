package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinBlockchainRepositoryImpl
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BitcoinModule {

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
        return BitcoinTransactionRepositoryImpl(bitcoinTransactionDao)
    }

    @Provides
    @Singleton
    fun provideBitcoinBlockchainRepository(
        @Named("bitcoinMainnet") mainnetApi: BitcoinApi,
        @Named("bitcoinTestnet") testnetApi: BitcoinApi,
        ioDispatcher: CoroutineDispatcher,
    ): BitcoinBlockchainRepository {
        return BitcoinBlockchainRepositoryImpl(mainnetApi, testnetApi, ioDispatcher)
    }
}