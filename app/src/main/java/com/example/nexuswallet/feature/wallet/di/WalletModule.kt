package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import com.example.nexuswallet.feature.authentication.domain.SecureStorage
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.wallet.data.local.BackupDao
import com.example.nexuswallet.feature.wallet.data.local.BalanceDao
import com.example.nexuswallet.feature.wallet.data.local.MnemonicDao
import com.example.nexuswallet.feature.wallet.data.local.SettingsDao
import com.example.nexuswallet.feature.wallet.data.local.TransactionDao
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.local.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.SendTransactionDao
import com.example.nexuswallet.feature.wallet.data.repository.BlockchainRepository
import com.example.nexuswallet.feature.wallet.data.repository.TransactionRepository
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
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
    fun provideSettingsDao(database: WalletDatabase): SettingsDao {
        return database.settingsDao()
    }

    @Provides
    @Singleton
    fun provideBackupDao(database: WalletDatabase): BackupDao {
        return database.backupDao()
    }

    @Provides
    @Singleton
    fun provideMnemonicDao(database: WalletDatabase): MnemonicDao {
        return database.mnemonicDao()
    }

    @Provides
    @Singleton
    fun provideSendTransactionDao(database: WalletDatabase): SendTransactionDao {
        return database.sendTransactionDao()
    }

    @Provides
    @Singleton
    fun provideWalletLocalDataSource(
        walletDao: WalletDao,
        balanceDao: BalanceDao,
        transactionDao: TransactionDao,
        settingsDao: SettingsDao,
        backupDao: BackupDao,
        mnemonicDao: MnemonicDao,
        sendTransactionDao: SendTransactionDao
    ): WalletLocalDataSource {
        return WalletLocalDataSource(
            walletDao = walletDao,
            balanceDao = balanceDao,
            transactionDao = transactionDao,
            settingsDao = settingsDao,
            backupDao = backupDao,
            mnemonicDao = mnemonicDao,
            sendTransactionDao = sendTransactionDao
        )
    }

    // NEW: Add TransactionLocalDataSource provider
    @Provides
    @Singleton
    fun provideTransactionLocalDataSource(
        transactionDao: TransactionDao,
        sendTransactionDao: SendTransactionDao
    ): TransactionLocalDataSource {
        return TransactionLocalDataSource(
            transactionDao = transactionDao,
            sendTransactionDao = sendTransactionDao
        )
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        localDataSource: WalletLocalDataSource,
        securityManager: SecurityManager,
        blockchainRepository: BlockchainRepository
    ): WalletRepository {
        return WalletRepository(localDataSource, securityManager, blockchainRepository)
    }

    @Provides
    @Singleton
    fun provideTransactionRepository(
        transactionLocalDataSource: TransactionLocalDataSource,
        blockchainRepository: BlockchainRepository,
        walletRepository: WalletRepository,
        securityManager: SecurityManager
    ): TransactionRepository {
        return TransactionRepository(
            localDataSource = transactionLocalDataSource,
            blockchainRepository = blockchainRepository,
            walletRepository = walletRepository
        )
    }

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context,
        walletLocalDataSource: WalletLocalDataSource
    ): SecureStorage {
        return SecureStorage(context, walletLocalDataSource)
    }

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context, secureStorage: SecureStorage): SecurityManager {
        return SecurityManager(context, secureStorage)
    }
}