package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionDao
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionEntity
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionDao
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionEntity
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionDao
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionEntity
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionDao
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionEntity
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
        SendTransactionEntity::class,
        BitcoinTransactionEntity::class,
        EthereumTransactionEntity::class,
        SolanaTransactionEntity::class,
        USDCTransactionEntity::class
    ],
    version = 6,
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
    abstract fun bitcoinTransactionDao(): BitcoinTransactionDao
    abstract fun ethereumTransactionDao(): EthereumTransactionDao
    abstract fun solanaTransactionDao(): SolanaTransactionDao
    abstract fun usdcTransactionDao(): USDCTransactionDao

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
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Migration from 5 to 6 (adding USDCTransaction table)
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `USDCSendTransaction` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `walletId` TEXT NOT NULL,
                        `fromAddress` TEXT NOT NULL,
                        `toAddress` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `note` TEXT,
                        `feeLevel` TEXT NOT NULL,
                        `amount` TEXT NOT NULL,
                        `amountDecimal` TEXT NOT NULL,
                        `contractAddress` TEXT NOT NULL,
                        `network` TEXT NOT NULL,
                        `gasPriceWei` TEXT NOT NULL,
                        `gasPriceGwei` TEXT NOT NULL,
                        `gasLimit` INTEGER NOT NULL,
                        `feeWei` TEXT NOT NULL,
                        `feeEth` TEXT NOT NULL,
                        `nonce` INTEGER NOT NULL,
                        `chainId` INTEGER NOT NULL,
                        `signedHex` TEXT,
                        `txHash` TEXT,
                        `ethereumTransactionId` TEXT
                    )
                """)
            }
        }
    }
}