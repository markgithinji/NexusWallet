package com.example.nexuswallet.feature.wallet.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toStorageString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID


val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isIncoming column to EthereumTransaction table
        database.execSQL("""
            ALTER TABLE EthereumTransaction 
            ADD COLUMN isIncoming INTEGER NOT NULL DEFAULT 0
        """)
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isIncoming column to BitcoinTransaction table
        database.execSQL("""
            ALTER TABLE BitcoinTransaction 
            ADD COLUMN isIncoming INTEGER NOT NULL DEFAULT 0
        """)
    }
}

// Migration from 9 to 10 - Normalize wallets and balances
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new tables
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `bitcoin_coins` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `derivationPath` TEXT NOT NULL,
                `network` TEXT NOT NULL,
                `xpub` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `ethereum_coins` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `derivationPath` TEXT NOT NULL,
                `network` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `solana_coins` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `derivationPath` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `usdc_coins` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `network` TEXT NOT NULL,
                `contractAddress` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        // Balance tables
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `bitcoin_balances` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `satoshis` TEXT NOT NULL,
                `btc` TEXT NOT NULL,
                `usdValue` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `ethereum_balances` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `wei` TEXT NOT NULL,
                `eth` TEXT NOT NULL,
                `usdValue` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `solana_balances` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `lamports` TEXT NOT NULL,
                `sol` TEXT NOT NULL,
                `usdValue` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `usdc_balances` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `walletId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `amount` TEXT NOT NULL,
                `amountDecimal` TEXT NOT NULL,
                `usdValue` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)

        // Create indices with UNIQUE constraint
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bitcoin_coins_walletId` ON `bitcoin_coins`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ethereum_coins_walletId` ON `ethereum_coins`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_solana_coins_walletId` ON `solana_coins`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_usdc_coins_walletId` ON `usdc_coins`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bitcoin_balances_walletId` ON `bitcoin_balances`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ethereum_balances_walletId` ON `ethereum_balances`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_solana_balances_walletId` ON `solana_balances`(`walletId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_usdc_balances_walletId` ON `usdc_balances`(`walletId`)")

        // First, create the new wallets table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `wallets_new` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `name` TEXT NOT NULL,
                `mnemonicHash` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isBackedUp` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)

        // Migrate data from old wallets table
        database.query("SELECT id, walletJson FROM wallets").use { cursor ->
            val idIndex = cursor.getColumnIndex("id")
            val jsonIndex = cursor.getColumnIndex("walletJson")

            val json = Json { ignoreUnknownKeys = true }

            while (cursor.moveToNext()) {
                val walletId = cursor.getString(idIndex)
                val walletJson = cursor.getString(jsonIndex)

                try {
                    val oldWallet = json.decodeFromString<Wallet>(walletJson)

                    // Insert into new wallets table
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO wallets_new (id, name, mnemonicHash, createdAt, isBackedUp, updatedAt)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        arrayOf<Any>(
                            oldWallet.id,
                            oldWallet.name,
                            oldWallet.mnemonicHash,
                            oldWallet.createdAt,
                            if (oldWallet.isBackedUp) 1 else 0,
                            System.currentTimeMillis()
                        )
                    )

                    // Insert Bitcoin coin if present
                    oldWallet.bitcoin?.let { bitcoin ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO bitcoin_coins (id, walletId, address, publicKey, derivationPath, network, xpub, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                bitcoin.address,
                                bitcoin.publicKey,
                                bitcoin.derivationPath,
                                bitcoin.network.name,
                                bitcoin.xpub,
                                System.currentTimeMillis()
                            )
                        )
                    }

                    // Insert Ethereum coin if present
                    oldWallet.ethereum?.let { ethereum ->
                        val networkJson = json.encodeToString(ethereum.network)
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO ethereum_coins (id, walletId, address, publicKey, derivationPath, network, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                ethereum.address,
                                ethereum.publicKey,
                                ethereum.derivationPath,
                                networkJson,
                                System.currentTimeMillis()
                            )
                        )
                    }

                    // Insert Solana coin if present
                    oldWallet.solana?.let { solana ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO solana_coins (id, walletId, address, publicKey, derivationPath, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                solana.address,
                                solana.publicKey,
                                solana.derivationPath,
                                System.currentTimeMillis()
                            )
                        )
                    }

                    // Insert USDC coin if present
                    oldWallet.usdc?.let { usdc ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO usdc_coins (id, walletId, address, publicKey, network, contractAddress, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                usdc.address,
                                usdc.publicKey,
                                usdc.network.toStorageString(),
                                usdc.contractAddress,
                                System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                }
            }
        }

        // Migrate balance data
        database.query("SELECT walletId, balanceJson FROM balances").use { cursor ->
            val walletIdIndex = cursor.getColumnIndex("walletId")
            val jsonIndex = cursor.getColumnIndex("balanceJson")

            val json = Json { ignoreUnknownKeys = true }

            while (cursor.moveToNext()) {
                val walletId = cursor.getString(walletIdIndex)
                val balanceJson = cursor.getString(jsonIndex)

                try {
                    val oldBalance = json.decodeFromString<WalletBalance>(balanceJson)

                    // Insert Bitcoin balance if present
                    oldBalance.bitcoin?.let { bitcoin ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO bitcoin_balances (id, walletId, address, satoshis, btc, usdValue, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                bitcoin.address,
                                bitcoin.satoshis,
                                bitcoin.btc,
                                bitcoin.usdValue,
                                oldBalance.lastUpdated
                            )
                        )
                    }

                    // Insert Ethereum balance if present
                    oldBalance.ethereum?.let { ethereum ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO ethereum_balances (id, walletId, address, wei, eth, usdValue, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                ethereum.address,
                                ethereum.wei,
                                ethereum.eth,
                                ethereum.usdValue,
                                oldBalance.lastUpdated
                            )
                        )
                    }

                    // Insert Solana balance if present
                    oldBalance.solana?.let { solana ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO solana_balances (id, walletId, address, lamports, sol, usdValue, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                solana.address,
                                solana.lamports,
                                solana.sol,
                                solana.usdValue,
                                oldBalance.lastUpdated
                            )
                        )
                    }

                    // Insert USDC balance if present
                    oldBalance.usdc?.let { usdc ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO usdc_balances (id, walletId, address, amount, amountDecimal, usdValue, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                            arrayOf<Any>(
                                UUID.randomUUID().toString(),
                                walletId,
                                usdc.address,
                                usdc.amount,
                                usdc.amountDecimal,
                                usdc.usdValue,
                                oldBalance.lastUpdated
                            )
                        )
                    }
                } catch (e: Exception) {
                }
            }
        }

        // Drop old tables
        database.execSQL("DROP TABLE IF EXISTS wallets")
        database.execSQL("DROP TABLE IF EXISTS balances")

        // Rename new wallets table
        database.execSQL("ALTER TABLE wallets_new RENAME TO wallets")
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