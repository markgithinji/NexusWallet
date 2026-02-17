package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import android.util.Log
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
import com.example.nexuswallet.feature.wallet.data.model.TransactionEntity
import com.example.nexuswallet.feature.wallet.data.model.WalletEntity
import org.json.JSONObject

@Database(
    entities = [
        WalletEntity::class,
        BalanceEntity::class,
        TransactionEntity::class,
        BackupEntity::class,
        BitcoinTransactionEntity::class,
        EthereumTransactionEntity::class,
        SolanaTransactionEntity::class,
        USDCTransactionEntity::class
    ],
    version = 9,  // Incremented from 8 to 9
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun balanceDao(): BalanceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun backupDao(): BackupDao
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
                    .addMigrations(
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the tables that are no longer needed
                database.execSQL("DROP TABLE IF EXISTS settings")
                database.execSQL("DROP TABLE IF EXISTS mnemonics")
                database.execSQL("DROP TABLE IF EXISTS SendTransactionEntity") // Check exact table name

                // Note: The walletJson already contains all the data we need
                // The removed tables contained redundant/legacy data
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

        // Migration from 7 to 8 (convert EthereumNetwork from string to sealed class)
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {

                database.query("SELECT id, walletJson FROM wallets").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val jsonIndex = cursor.getColumnIndex("walletJson")

                    var successCount = 0
                    var errorCount = 0

                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex)
                        val oldJson = cursor.getString(jsonIndex)

                        try {
                            val newJson = convertWalletJsonForMigration(oldJson)
                            database.execSQL(
                                "UPDATE wallets SET walletJson = ? WHERE id = ?",
                                arrayOf(newJson, id)
                            )
                            successCount++
                        } catch (e: Exception) {
                            errorCount++
                        }
                    }
                }
            }

            private fun convertWalletJsonForMigration(oldJson: String): String {
                val jsonObject = JSONObject(oldJson)

                // Convert ethereum network if present
                if (jsonObject.has("ethereum")) {
                    val ethObj = jsonObject.getJSONObject("ethereum")
                    if (ethObj.has("network")) {
                        val oldNetwork = ethObj.getString("network")
                        val newNetworkObj = convertOldNetworkToNew(oldNetwork)
                        ethObj.put("network", newNetworkObj)
                    }
                }

                // Convert usdc network if present
                if (jsonObject.has("usdc")) {
                    val usdcObj = jsonObject.getJSONObject("usdc")
                    if (usdcObj.has("network")) {
                        val oldNetwork = usdcObj.getString("network")
                        val newNetworkObj = convertOldNetworkToNew(oldNetwork)
                        usdcObj.put("network", newNetworkObj)
                    }
                }

                return jsonObject.toString()
            }

            private fun convertOldNetworkToNew(oldNetwork: String): JSONObject {
                return when (oldNetwork.uppercase()) {
                    "MAINNET" -> JSONObject().apply {
                        put("type", "Mainnet")
                        put("chainId", "1")
                        put("usdcContractAddress", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                        put("isTestnet", false)
                        put("displayName", "Mainnet")
                    }
                    "SEPOLIA" -> JSONObject().apply {
                        put("type", "Sepolia")
                        put("chainId", "11155111")
                        put("usdcContractAddress", "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238")
                        put("isTestnet", true)
                        put("displayName", "Sepolia")
                    }
                    // Handle other networks by mapping to appropriate defaults
                    "POLYGON", "BSC", "ARBITRUM", "OPTIMISM" -> {
                        JSONObject().apply {
                            put("type", "Mainnet")
                            put("chainId", "1")
                            put("usdcContractAddress", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                            put("isTestnet", false)
                            put("displayName", "Mainnet")
                        }
                    }
                    "GOERLI" -> {
                        JSONObject().apply {
                            put("type", "Sepolia")
                            put("chainId", "11155111")
                            put("usdcContractAddress", "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238")
                            put("isTestnet", true)
                            put("displayName", "Sepolia")
                        }
                    }
                    else -> {
                        JSONObject().apply {
                            put("type", "Sepolia")
                            put("chainId", "11155111")
                            put("usdcContractAddress", "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238")
                            put("isTestnet", true)
                            put("displayName", "Sepolia")
                        }
                    }
                }
            }
        }
    }
}