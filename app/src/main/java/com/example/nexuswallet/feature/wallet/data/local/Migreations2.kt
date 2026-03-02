package com.example.nexuswallet.feature.wallet.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toStorageString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID

// Add this to your WalletDatabase companion object
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ============ BITCOIN BALANCES MIGRATION ============

        // 1. Create new bitcoin_balances table with coinId reference
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `bitcoin_balances_new` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `coinId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `satoshis` TEXT NOT NULL,
                `btc` TEXT NOT NULL,
                `usdValue` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`coinId`) REFERENCES `bitcoin_coins`(`id`) ON DELETE CASCADE
            )
        """)

        // 2. Create index on coinId
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_bitcoin_balances_new_coinId` ON `bitcoin_balances_new`(`coinId`)")

        // 3. Migrate existing bitcoin balances to new structure
        database.execSQL("""
            INSERT INTO `bitcoin_balances_new` (`id`, `coinId`, `address`, `satoshis`, `btc`, `usdValue`, `updatedAt`)
            SELECT 
                bb.`id`,
                bc.`id` as `coinId`,
                bb.`address`,
                bb.`satoshis`,
                bb.`btc`,
                bb.`usdValue`,
                bb.`updatedAt`
            FROM `bitcoin_balances` bb
            INNER JOIN `bitcoin_coins` bc ON bb.`walletId` = bc.`walletId`
            WHERE bb.`address` = bc.`address`
        """)

        // 4. Drop old bitcoin_balances table
        database.execSQL("DROP TABLE IF EXISTS `bitcoin_balances`")

        // 5. Rename new table to original name
        database.execSQL("ALTER TABLE `bitcoin_balances_new` RENAME TO `bitcoin_balances`")

        // ============ SOLANA BALANCES MIGRATION ============

        // 1. Create new solana_balances table with coinId reference
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `solana_balances_new` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `coinId` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `lamports` TEXT NOT NULL,
                `sol` TEXT NOT NULL,
                `usdValue` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`coinId`) REFERENCES `solana_coins`(`id`) ON DELETE CASCADE
            )
        """)

        // 2. Create index on coinId
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_solana_balances_new_coinId` ON `solana_balances_new`(`coinId`)")

        // 3. Migrate existing solana balances to new structure
        database.execSQL("""
            INSERT INTO `solana_balances_new` (`id`, `coinId`, `address`, `lamports`, `sol`, `usdValue`, `updatedAt`)
            SELECT 
                sb.`id`,
                sc.`id` as `coinId`,
                sb.`address`,
                sb.`lamports`,
                sb.`sol`,
                sb.`usdValue`,
                sb.`updatedAt`
            FROM `solana_balances` sb
            INNER JOIN `solana_coins` sc ON sb.`walletId` = sc.`walletId`
            WHERE sb.`address` = sc.`address`
        """)

        // 4. Drop old solana_balances table
        database.execSQL("DROP TABLE IF EXISTS `solana_balances`")

        // 5. Rename new table to original name
        database.execSQL("ALTER TABLE `solana_balances_new` RENAME TO `solana_balances`")

        // ============ HANDLE ANY ORPHANED BALANCES ============

        // Delete any bitcoin balances that couldn't be migrated (no matching coin)
        database.execSQL("""
            DELETE FROM `bitcoin_balances` 
            WHERE `coinId` NOT IN (SELECT `id` FROM `bitcoin_coins`)
        """)

        // Delete any solana balances that couldn't be migrated
        database.execSQL("""
            DELETE FROM `solana_balances` 
            WHERE `coinId` NOT IN (SELECT `id` FROM `solana_coins`)
        """)
    }
}