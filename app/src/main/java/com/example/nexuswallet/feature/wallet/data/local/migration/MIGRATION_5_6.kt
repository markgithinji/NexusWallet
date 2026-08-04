package com.example.nexuswallet.feature.wallet.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `address_book` (
                `id` TEXT PRIMARY KEY NOT NULL,
                `alias` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
    }
}
