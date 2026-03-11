package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.WalletDao
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepositoryImpl
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.domain.SyncWalletBalancesUseCaseImpl
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
    fun provideWalletDataSource(
        walletDao: WalletDao,
        bitcoinCoinDao: BitcoinCoinDao,
        solanaCoinDao: SolanaCoinDao,
        evmTokenDao: EVMTokenDao,
        splTokenDao: SPLTokenDao
    ): WalletDataSource {
        return WalletDataSourceImpl(
            walletDao = walletDao,
            bitcoinCoinDao = bitcoinCoinDao,
            solanaCoinDao = solanaCoinDao,
            evmTokenDao = evmTokenDao,
            splTokenDao = splTokenDao
        )
    }

    @Provides
    @Singleton
    fun provideBalanceDataSource(
        bitcoinCoinDao: BitcoinCoinDao,
        solanaCoinDao: SolanaCoinDao,
        bitcoinBalanceDao: BitcoinBalanceDao,
        solanaBalanceDao: SolanaBalanceDao,
        evmTokenDao: EVMTokenDao,
        evmBalanceDao: EVMBalanceDao
    ): BalanceDataSource {
        return BalanceDataSourceImpl(
            bitcoinCoinDao = bitcoinCoinDao,
            solanaCoinDao = solanaCoinDao,
            bitcoinBalanceDao = bitcoinBalanceDao,
            solanaBalanceDao = solanaBalanceDao,
            evmTokenDao = evmTokenDao,
            evmBalanceDao = evmBalanceDao
        )
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        walletDataSource: WalletDataSource,
        balanceDataSource: BalanceDataSource
    ): WalletRepository {
        return WalletRepositoryImpl(
            walletDataSource = walletDataSource,
            balanceDataSource = balanceDataSource
        )
    }

    // === Use Cases ===
    @Provides
    @Singleton
    fun provideSyncWalletBalancesUseCase(
        walletDataSource: WalletDataSource,
        balanceDataSource: BalanceDataSource,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): SyncWalletBalancesUseCase {
        return SyncWalletBalancesUseCaseImpl(
            walletDataSource = walletDataSource,
            balanceDataSource = balanceDataSource,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }
}