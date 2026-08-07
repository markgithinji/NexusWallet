package com.example.nexuswallet.feature.wallet.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Migrate evm_balances (Add contractAddress and change usdValue type)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `evm_balances_new` (
                `id` TEXT NOT NULL, 
                `walletId` TEXT NOT NULL, 
                `evmTokenType` TEXT NOT NULL, 
                `network` TEXT NOT NULL, 
                `address` TEXT NOT NULL, 
                `contractAddress` TEXT NOT NULL, 
                `balanceWei` TEXT NOT NULL, 
                `balanceDecimal` TEXT NOT NULL, 
                `usdValue` TEXT NOT NULL, 
                `updatedAt` INTEGER NOT NULL, 
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON DELETE CASCADE
            )
        """)
        
        // Copy data, converting usdValue to String and setting default contractAddress
        db.execSQL("""
            INSERT INTO evm_balances_new (id, walletId, evmTokenType, network, address, contractAddress, balanceWei, balanceDecimal, usdValue, updatedAt)
            SELECT id, walletId, evmTokenType, network, address, '', balanceWei, balanceDecimal, CAST(usdValue AS TEXT), updatedAt
            FROM evm_balances
        """)
        
        db.execSQL("DROP TABLE evm_balances")
        db.execSQL("ALTER TABLE evm_balances_new RENAME TO evm_balances")
        
        // Re-create indices for evm_balances
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evm_balances_walletId_evmTokenType_network` ON `evm_balances` (`walletId`, `evmTokenType`, `network`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evm_balances_evmTokenType` ON `evm_balances` (`evmTokenType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evm_balances_network` ON `evm_balances` (`network`)")

        // 2. Migrate bitcoin_balances (Change usdValue type)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bitcoin_balances_new` (
                `id` TEXT NOT NULL, 
                `coinId` TEXT NOT NULL, 
                `address` TEXT NOT NULL, 
                `satoshis` TEXT NOT NULL, 
                `btc` TEXT NOT NULL, 
                `usdValue` TEXT NOT NULL, 
                `updatedAt` INTEGER NOT NULL, 
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`coinId`) REFERENCES `bitcoin_coins`(`id`) ON DELETE CASCADE
            )
        """)
        
        db.execSQL("""
            INSERT INTO bitcoin_balances_new (id, coinId, address, satoshis, btc, usdValue, updatedAt)
            SELECT id, coinId, address, satoshis, btc, CAST(usdValue AS TEXT), updatedAt
            FROM bitcoin_balances
        """)
        
        db.execSQL("DROP TABLE bitcoin_balances")
        db.execSQL("ALTER TABLE bitcoin_balances_new RENAME TO bitcoin_balances")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bitcoin_balances_coinId` ON `bitcoin_balances` (`coinId`)")

        // 3. Migrate solana_balances (Change usdValue type)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `solana_balances_new` (
                `id` TEXT NOT NULL, 
                `coinId` TEXT NOT NULL, 
                `address` TEXT NOT NULL, 
                `lamports` TEXT NOT NULL, 
                `sol` TEXT NOT NULL, 
                `usdValue` TEXT NOT NULL, 
                `updatedAt` INTEGER NOT NULL, 
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`coinId`) REFERENCES `solana_coins`(`id`) ON DELETE CASCADE
            )
        """)
        
        db.execSQL("""
            INSERT INTO solana_balances_new (id, coinId, address, lamports, sol, usdValue, updatedAt)
            SELECT id, coinId, address, lamports, sol, CAST(usdValue AS TEXT), updatedAt
            FROM solana_balances
        """)
        
        db.execSQL("DROP TABLE solana_balances")
        db.execSQL("ALTER TABLE solana_balances_new RENAME TO solana_balances")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_solana_balances_coinId` ON `solana_balances` (`coinId`)")
    }
}
