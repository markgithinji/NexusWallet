package com.example.nexuswallet.feature.wallet.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // BitcoinCoinEntity - network column type change
        database.execSQL("""
                    CREATE TABLE bitcoin_coins_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        walletId TEXT NOT NULL,
                        address TEXT NOT NULL,
                        publicKey TEXT NOT NULL,
                        derivationPath TEXT NOT NULL,
                        network TEXT NOT NULL,
                        xpub TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(walletId) REFERENCES wallet_entity(id) ON DELETE CASCADE
                    )
                """.trimIndent())

        database.execSQL("""
                    INSERT INTO bitcoin_coins_new (id, walletId, address, publicKey, derivationPath, network, xpub, updatedAt)
                    SELECT id, walletId, address, publicKey, derivationPath, 
                           CASE network 
                               WHEN 'BitcoinMainnet' THEN 'Mainnet'
                               WHEN 'BitcoinTestnet' THEN 'Testnet'
                               ELSE network
                           END,
                           xpub, updatedAt
                    FROM bitcoin_coins
                """.trimIndent())

        database.execSQL("DROP TABLE bitcoin_coins")
        database.execSQL("ALTER TABLE bitcoin_coins_new RENAME TO bitcoin_coins")
        database.execSQL("CREATE INDEX index_bitcoin_coins_walletId ON bitcoin_coins(walletId)")

        // SolanaCoinEntity - network column type change
        database.execSQL("""
                    CREATE TABLE solana_coins_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        walletId TEXT NOT NULL,
                        address TEXT NOT NULL,
                        publicKey TEXT NOT NULL,
                        derivationPath TEXT NOT NULL,
                        network TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(walletId) REFERENCES wallet_entity(id) ON DELETE CASCADE
                    )
                """.trimIndent())

        database.execSQL("""
                    INSERT INTO solana_coins_new (id, walletId, address, publicKey, derivationPath, network, updatedAt)
                    SELECT id, walletId, address, publicKey, derivationPath, 
                           CASE network 
                               WHEN 'SolanaMainnet' THEN 'Mainnet'
                               WHEN 'SolanaDevnet' THEN 'Devnet'
                               ELSE network
                           END,
                           updatedAt
                    FROM solana_coins
                """.trimIndent())

        database.execSQL("DROP TABLE solana_coins")
        database.execSQL("ALTER TABLE solana_coins_new RENAME TO solana_coins")
        database.execSQL("CREATE INDEX index_solana_coins_walletId ON solana_coins(walletId)")

        // EVMTokenEntity - network column type change
        database.execSQL("""
                    CREATE TABLE evm_tokens_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        walletId TEXT NOT NULL,
                        address TEXT NOT NULL,
                        publicKey TEXT NOT NULL,
                        derivationPath TEXT NOT NULL,
                        network TEXT NOT NULL,
                        contractAddress TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        name TEXT NOT NULL,
                        decimals INTEGER NOT NULL,
                        tokenType TEXT NOT NULL,
                        externalId TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(walletId) REFERENCES wallet_entity(id) ON DELETE CASCADE
                    )
                """.trimIndent())

        database.execSQL("""
                    INSERT INTO evm_tokens_new (id, walletId, address, publicKey, derivationPath, network, contractAddress, symbol, name, decimals, tokenType, externalId, updatedAt)
                    SELECT id, walletId, address, publicKey, derivationPath, 
                           CASE network 
                               WHEN 'EthereumMainnet' THEN 'Mainnet'
                               WHEN 'EthereumSepolia' THEN 'Sepolia'
                               ELSE network
                           END,
                           contractAddress, symbol, name, decimals, tokenType, externalId, updatedAt
                    FROM evm_tokens
                """.trimIndent())

        database.execSQL("DROP TABLE evm_tokens")
        database.execSQL("ALTER TABLE evm_tokens_new RENAME TO evm_tokens")
        database.execSQL("CREATE INDEX index_evm_tokens_walletId ON evm_tokens(walletId)")
        database.execSQL("CREATE UNIQUE INDEX index_evm_tokens_walletId_contractAddress_network ON evm_tokens(walletId, contractAddress, network)")
        database.execSQL("CREATE INDEX index_evm_tokens_externalId ON evm_tokens(externalId)")
    }
}