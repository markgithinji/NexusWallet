package com.example.nexuswallet.feature.wallet.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE solana_transactions_new (
                id TEXT PRIMARY KEY NOT NULL,
                walletId TEXT NOT NULL,
                fromAddress TEXT NOT NULL,
                toAddress TEXT NOT NULL,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                note TEXT,
                feeLevel TEXT NOT NULL,
                amountLamports INTEGER NOT NULL,
                amountSol TEXT NOT NULL,
                feeLamports INTEGER NOT NULL,
                feeSol TEXT NOT NULL,
                signature TEXT,
                network TEXT NOT NULL,
                isIncoming INTEGER NOT NULL DEFAULT 0,
                tokenMint TEXT,
                tokenSymbol TEXT,
                tokenDecimals INTEGER,
                slot INTEGER,
                blockTime INTEGER
            )
        """)

        // Copy data from old table to new table
        database.execSQL("""
            INSERT INTO solana_transactions_new (
                id, walletId, fromAddress, toAddress, status, timestamp, note, 
                feeLevel, amountLamports, amountSol, feeLamports, feeSol, signature, 
                network, isIncoming, tokenMint, tokenSymbol, tokenDecimals, slot, blockTime
            )
            SELECT 
                id, walletId, fromAddress, toAddress, status, timestamp, note,
                feeLevel, amountLamports, amountSol, feeLamports, feeSol, signature,
                network, isIncoming, tokenMint, tokenSymbol, tokenDecimals, slot, blockTime
            FROM solana_transactions
        """)

        // Drop old table
        database.execSQL("DROP TABLE solana_transactions")

        // Rename new table to original name
        database.execSQL("ALTER TABLE solana_transactions_new RENAME TO solana_transactions")
    }
}