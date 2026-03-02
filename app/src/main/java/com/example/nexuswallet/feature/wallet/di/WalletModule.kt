package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.local.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSourceImpl
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepositoryImpl
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SyncWalletBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.SyncWalletBalancesUseCaseImpl
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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

    // === Data Sources ===
    @Provides
    @Singleton
    fun provideWalletLocalDataSource(
        walletDao: WalletDao,
        bitcoinCoinDao: BitcoinCoinDao,
        solanaCoinDao: SolanaCoinDao,
        splTokenDao: SPLTokenDao,
        evmTokenDao: EVMTokenDao,
        bitcoinBalanceDao: BitcoinBalanceDao,
        solanaBalanceDao: SolanaBalanceDao,
        evmBalanceDao: EVMBalanceDao,
    ): WalletLocalDataSource {
        return WalletLocalDataSourceImpl(
            walletDao = walletDao,
            bitcoinCoinDao = bitcoinCoinDao,
            solanaCoinDao = solanaCoinDao,
            splTokenDao = splTokenDao,
            evmTokenDao = evmTokenDao,
            bitcoinBalanceDao = bitcoinBalanceDao,
            solanaBalanceDao = solanaBalanceDao,
            evmBalanceDao = evmBalanceDao
        )
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        localDataSource: WalletLocalDataSource
    ): WalletRepository {
        return WalletRepositoryImpl(
            localDataSource = localDataSource
        )
    }

    // === Use Cases ===
    @Provides
    @Singleton
    fun provideSyncWalletBalancesUseCase(
        localDataSource: WalletLocalDataSource,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): SyncWalletBalancesUseCase {
        return SyncWalletBalancesUseCaseImpl(
            localDataSource = localDataSource,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }
}