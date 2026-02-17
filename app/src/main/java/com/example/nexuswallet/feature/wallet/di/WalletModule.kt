package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.local.BackupDao
import com.example.nexuswallet.feature.wallet.data.local.BalanceDao
import com.example.nexuswallet.feature.wallet.data.local.TransactionDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.SyncWalletBalancesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWalletDatabase(@ApplicationContext context: Context): WalletDatabase {
        return WalletDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideWalletDao(database: WalletDatabase): WalletDao {
        return database.walletDao()
    }

    @Provides
    @Singleton
    fun provideBalanceDao(database: WalletDatabase): BalanceDao {
        return database.balanceDao()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: WalletDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideBackupDao(database: WalletDatabase): BackupDao {
        return database.backupDao()
    }

    @Provides
    @Singleton
    fun provideWalletLocalDataSource(
        walletDao: WalletDao,
        balanceDao: BalanceDao,
        transactionDao: TransactionDao,
        backupDao: BackupDao,
    ): WalletLocalDataSource {
        return WalletLocalDataSource(
            walletDao = walletDao,
            balanceDao = balanceDao,
            transactionDao = transactionDao,
            backupDao = backupDao,
        )
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        localDataSource: WalletLocalDataSource,
        securityManager: SecurityManager,
        keyManager: KeyManager
    ): WalletRepository {
        return WalletRepository(localDataSource, securityManager, keyManager)
    }

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context,
        walletLocalDataSource: WalletLocalDataSource
    ): SecurityPreferencesRepository {
        return SecurityPreferencesRepository(context, walletLocalDataSource)
    }

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context, securityPreferencesRepository: SecurityPreferencesRepository): SecurityManager {
        return SecurityManager(context, securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideKeyManager(
        securityManager: SecurityManager
    ): KeyManager {
        return KeyManager(securityManager)
    }

    @Provides
    @Singleton
    fun provideWeb3j(): Web3j {
        return Web3j.build(
            HttpService("https://mainnet.infura.io/v3/demo") // TODO: Use key
        )
    }

    @Provides
    @Singleton
    fun provideSyncWalletBalancesUseCase(
        localDataSource: WalletLocalDataSource,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        usdcBlockchainRepository: USDCBlockchainRepository
    ): SyncWalletBalancesUseCase {
        return SyncWalletBalancesUseCase(
            localDataSource = localDataSource,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            usdcBlockchainRepository = usdcBlockchainRepository
        )
    }


}