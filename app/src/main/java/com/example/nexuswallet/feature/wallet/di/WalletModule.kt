package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import com.example.nexuswallet.feature.authentication.domain.SecureStorage
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.wallet.data.local.BackupDao
import com.example.nexuswallet.feature.wallet.data.local.BalanceDao
import com.example.nexuswallet.feature.wallet.data.local.MnemonicDao
import com.example.nexuswallet.feature.wallet.data.local.SettingsDao
import com.example.nexuswallet.feature.wallet.data.local.TransactionDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
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
    fun provideWalletLocalDataSource(
        walletDao: WalletDao,
        balanceDao: BalanceDao,
        transactionDao: TransactionDao,
        settingsDao: SettingsDao,
        backupDao: BackupDao,
        mnemonicDao: MnemonicDao
    ): WalletLocalDataSource {
        return WalletLocalDataSource(
            walletDao = walletDao,
            balanceDao = balanceDao,
            transactionDao = transactionDao,
            settingsDao = settingsDao,
            backupDao = backupDao,
            mnemonicDao = mnemonicDao
        )
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        localDataSource: WalletLocalDataSource,
        securityManager: SecurityManager
    ): WalletRepository {
        return WalletRepository(localDataSource, securityManager)
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
    fun provideSecurityManager(@ApplicationContext context: Context, secureStorage : SecureStorage): SecurityManager {
        return SecurityManager(context, secureStorage)
    }
}