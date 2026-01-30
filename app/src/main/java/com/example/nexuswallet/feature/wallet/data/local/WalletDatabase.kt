package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.nexuswallet.feature.wallet.data.model.BackupEntity
import com.example.nexuswallet.feature.wallet.data.model.BalanceEntity
import com.example.nexuswallet.feature.wallet.data.model.MnemonicEntity
import com.example.nexuswallet.feature.wallet.data.model.SendTransactionDao
import com.example.nexuswallet.feature.wallet.data.model.SendTransactionEntity
import com.example.nexuswallet.feature.wallet.data.model.SettingsEntity
import com.example.nexuswallet.feature.wallet.data.model.TransactionEntity
import com.example.nexuswallet.feature.wallet.data.model.WalletEntity

@Database(
    entities = [
        WalletEntity::class,
        BalanceEntity::class,
        TransactionEntity::class,
        SettingsEntity::class,
        BackupEntity::class,
        MnemonicEntity::class,
        SendTransactionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun balanceDao(): BalanceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupDao(): BackupDao
    abstract fun mnemonicDao(): MnemonicDao
    abstract fun sendTransactionDao(): SendTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: WalletDatabase? = null

        fun getDatabase(context: Context): WalletDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "wallet_database"
                )
                    .fallbackToDestructiveMigration() // This will delete old data on upgrade
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}