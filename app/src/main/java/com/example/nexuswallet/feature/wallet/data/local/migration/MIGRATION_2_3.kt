package com.example.nexuswallet.feature.wallet.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Add this to your companion object
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // BitcoinTransactionEntity - network column type change to enum
        database.execSQL("""
            CREATE TABLE BitcoinTransaction_new (
                id TEXT PRIMARY KEY NOT NULL,
                walletId TEXT NOT NULL,
                fromAddress TEXT NOT NULL,
                toAddress TEXT NOT NULL,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                note TEXT,
                feeLevel TEXT NOT NULL,
                amountSatoshis INTEGER NOT NULL,
                amountBtc TEXT NOT NULL,
                feeSatoshis INTEGER NOT NULL,
                feeBtc TEXT NOT NULL,
                feePerByte REAL NOT NULL,
                estimatedSize INTEGER NOT NULL,
                signedHex TEXT,
                txHash TEXT,
                network TEXT NOT NULL,
                isIncoming INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(walletId) REFERENCES WalletEntity(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Copy data with network mapping (keep existing values)
        database.execSQL("""
            INSERT INTO BitcoinTransaction_new 
            SELECT id, walletId, fromAddress, toAddress, status, timestamp, note, feeLevel,
                   amountSatoshis, amountBtc, feeSatoshis, feeBtc, feePerByte, estimatedSize,
                   signedHex, txHash, network, isIncoming
            FROM BitcoinTransaction
        """.trimIndent())

        database.execSQL("DROP TABLE BitcoinTransaction")
        database.execSQL("ALTER TABLE BitcoinTransaction_new RENAME TO BitcoinTransaction")
        database.execSQL("CREATE INDEX index_BitcoinTransaction_walletId ON BitcoinTransaction(walletId)")

        // EVMTransactionEntity - major changes
        database.execSQL("""
            CREATE TABLE EVMTransaction_new (
                id TEXT PRIMARY KEY NOT NULL,
                walletId TEXT NOT NULL,
                fromAddress TEXT NOT NULL,
                toAddress TEXT NOT NULL,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                note TEXT,
                feeLevel TEXT NOT NULL,
                amountWei TEXT NOT NULL,
                amountDecimal TEXT NOT NULL,
                gasPriceWei TEXT NOT NULL,
                gasPriceGwei TEXT NOT NULL,
                gasLimit INTEGER NOT NULL,
                feeWei TEXT NOT NULL,
                feeEth TEXT NOT NULL,
                nonce INTEGER NOT NULL,
                chainId INTEGER NOT NULL,
                signedHex TEXT,
                txHash TEXT,
                network TEXT NOT NULL,
                data TEXT NOT NULL,
                isIncoming INTEGER NOT NULL DEFAULT 0,
                tokenContract TEXT,
                tokenSymbol TEXT,
                tokenDecimals INTEGER,
                tokenExternalId TEXT,
                transactionType TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(walletId) REFERENCES WalletEntity(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Copy data from old EVMTransaction table
        // We need to determine transactionType based on tokenContract presence
        database.execSQL("""
            INSERT INTO EVMTransaction_new (
                id, walletId, fromAddress, toAddress, status, timestamp, note, feeLevel,
                amountWei, amountDecimal, gasPriceWei, gasPriceGwei, gasLimit, feeWei, feeEth,
                nonce, chainId, signedHex, txHash, network, data, isIncoming,
                tokenContract, tokenSymbol, tokenDecimals, tokenExternalId,
                transactionType, updatedAt
            )
            SELECT 
                id, walletId, fromAddress, toAddress, status, timestamp, note, feeLevel,
                amountWei, amountDecimal, gasPriceWei, gasPriceGwei, gasLimit, feeWei, feeEth,
                nonce, chainId, signedHex, txHash, network, data, isIncoming,
                tokenContract, tokenSymbol, tokenDecimals, tokenExternalId,
                CASE 
                    WHEN tokenContract IS NULL THEN 'NATIVE_ETH'
                    ELSE 'TOKEN'
                END as transactionType,
                updatedAt
            FROM EVMTransaction
        """.trimIndent())

        database.execSQL("DROP TABLE EVMTransaction")
        database.execSQL("ALTER TABLE EVMTransaction_new RENAME TO EVMTransaction")
        database.execSQL("CREATE INDEX index_EVMTransaction_walletId ON EVMTransaction(walletId)")
        database.execSQL("CREATE UNIQUE INDEX index_EVMTransaction_txHash ON EVMTransaction(txHash)")
        database.execSQL("CREATE INDEX index_EVMTransaction_walletId_tokenExternalId ON EVMTransaction(walletId, tokenExternalId)")
    }
}