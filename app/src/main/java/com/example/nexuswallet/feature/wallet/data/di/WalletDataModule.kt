package com.example.nexuswallet.feature.wallet.data.di

import android.content.Context
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.datasource.BalanceDataSourceImpl
import com.example.nexuswallet.feature.wallet.data.local.datasource.WalletDataSourceImpl
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepositoryImpl
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletDataModule {

    @Binds
    @Singleton
    abstract fun bindWalletDataSource(
        impl: WalletDataSourceImpl
    ): WalletDataSource

    @Binds
    @Singleton
    abstract fun bindBalanceDataSource(
        impl: BalanceDataSourceImpl
    ): BalanceDataSource

    @Binds
    @Singleton
    abstract fun bindWalletRepository(
        impl: WalletRepositoryImpl
    ): WalletRepository

    companion object {
        @Provides
        @Singleton
        fun provideWalletDatabase(@ApplicationContext context: Context): WalletDatabase {
            return WalletDatabase.getDatabase(context)
        }

        // === Wallet DAOs ===
        @Provides
        @Singleton
        fun provideWalletDao(database: WalletDatabase): WalletDao {
            return database.walletDao()
        }

        @Provides
        @Singleton
        fun provideBitcoinCoinDao(database: WalletDatabase): BitcoinCoinDao {
            return database.bitcoinCoinDao()
        }

        @Provides
        @Singleton
        fun provideSolanaCoinDao(database: WalletDatabase): SolanaCoinDao {
            return database.solanaCoinDao()
        }

        @Provides
        @Singleton
        fun provideSPLTokenDao(database: WalletDatabase): SPLTokenDao {
            return database.splTokenDao()
        }

        @Provides
        @Singleton
        fun provideEVMTokenDao(database: WalletDatabase): EVMTokenDao {
            return database.evmTokenDao()
        }

        // === Balance DAOs ===
        @Provides
        @Singleton
        fun provideBitcoinBalanceDao(database: WalletDatabase): BitcoinBalanceDao {
            return database.bitcoinBalanceDao()
        }

        @Provides
        @Singleton
        fun provideSolanaBalanceDao(database: WalletDatabase): SolanaBalanceDao {
            return database.solanaBalanceDao()
        }

        @Provides
        @Singleton
        fun provideEVMBalanceDao(database: WalletDatabase): EVMBalanceDao {
            return database.evmBalanceDao()
        }
    }
}
