package com.example.nexuswallet.feature.wallet.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new table with enum column
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
                FOREIGN KEY(walletId) REFERENCES WalletEntity(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Copy data with tokenType conversion (existing string values map to enum names)
        database.execSQL("""
            INSERT INTO evm_tokens_new (
                id, walletId, address, publicKey, derivationPath, network, 
                contractAddress, symbol, name, decimals, tokenType, externalId, updatedAt
            )
            SELECT 
                id, walletId, address, publicKey, derivationPath, network,
                contractAddress, symbol, name, decimals, tokenType, externalId, updatedAt
            FROM evm_tokens
        """.trimIndent())

        // Drop old table and rename new one
        database.execSQL("DROP TABLE evm_tokens")
        database.execSQL("ALTER TABLE evm_tokens_new RENAME TO evm_tokens")

        // Recreate indices
        database.execSQL("CREATE INDEX index_evm_tokens_walletId ON evm_tokens(walletId)")
        database.execSQL("CREATE UNIQUE INDEX index_evm_tokens_walletId_contractAddress_network ON evm_tokens(walletId, contractAddress, network)")
        database.execSQL("CREATE INDEX index_evm_tokens_externalId ON evm_tokens(externalId)")
    }
}