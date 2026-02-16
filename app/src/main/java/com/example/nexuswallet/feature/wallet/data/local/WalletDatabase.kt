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
    version = 7,
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
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
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

        // Migration from 6 to 7 (convert SolanaTransaction BLOB columns to TEXT)
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new table with TEXT columns instead of BLOB
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `SolanaTransaction_new` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `walletId` TEXT NOT NULL,
                        `fromAddress` TEXT NOT NULL,
                        `toAddress` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `note` TEXT,
                        `feeLevel` TEXT NOT NULL,
                        `amountLamports` INTEGER NOT NULL,
                        `amountSol` TEXT NOT NULL,
                        `feeLamports` INTEGER NOT NULL,
                        `feeSol` TEXT NOT NULL,
                        `blockhash` TEXT NOT NULL,
                        `signedData` TEXT,
                        `signature` TEXT,
                        `network` TEXT NOT NULL
                    )
                """)

                // Copy data from old table to new table, converting BLOB to HEX string
                database.execSQL("""
                    INSERT INTO SolanaTransaction_new (
                        id, walletId, fromAddress, toAddress, status, timestamp, note, feeLevel,
                        amountLamports, amountSol, feeLamports, feeSol, blockhash,
                        signedData, signature, network
                    )
                    SELECT 
                        id, walletId, fromAddress, toAddress, status, timestamp, note, feeLevel,
                        amountLamports, amountSol, feeLamports, feeSol, blockhash,
                        CASE WHEN signedData IS NOT NULL THEN hex(signedData) ELSE NULL END,
                        CASE WHEN signature IS NOT NULL THEN hex(signature) ELSE NULL END,
                        network
                    FROM SolanaTransaction
                """)

                // Drop the old table
                database.execSQL("DROP TABLE SolanaTransaction")

                // Rename new table to original name
                database.execSQL("ALTER TABLE SolanaTransaction_new RENAME TO SolanaTransaction")
            }
        }
    }
}