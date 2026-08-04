package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nexuswallet.feature.bitcoin.data.local.BitcoinTransactionDao
import com.example.nexuswallet.feature.bitcoin.data.local.BitcoinTransactionEntity
import com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionDao
import com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionEntity
import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionDao
import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionEntity
import com.example.nexuswallet.feature.wallet.data.local.dao.AddressBookDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.entity.AddressBookEntryEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.SPLTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import com.example.nexuswallet.feature.wallet.data.local.migration.*

@Database(
    entities = [
        // Wallet core
        WalletEntity::class,

        // Bitcoin
        BitcoinCoinEntity::class,
        BitcoinBalanceEntity::class,
        BitcoinTransactionEntity::class,

        // Solana
        SolanaCoinEntity::class,
        SolanaBalanceEntity::class,
        SolanaTransactionEntity::class,

        // Solana SPL Tokens
        SPLTokenEntity::class,

        // EVM
        EVMTokenEntity::class,
        EVMBalanceEntity::class,
        EVMTransactionEntity::class,

        // Address Book
        AddressBookEntryEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WalletDatabase : RoomDatabase() {
    // Wallet DAOs
    abstract fun walletDao(): WalletDao

    // Address Book DAO
    abstract fun addressBookDao(): AddressBookDao

    // Bitcoin DAOs
    abstract fun bitcoinCoinDao(): BitcoinCoinDao
    abstract fun bitcoinBalanceDao(): BitcoinBalanceDao
    abstract fun bitcoinTransactionDao(): BitcoinTransactionDao

    // Solana DAOs
    abstract fun solanaCoinDao(): SolanaCoinDao
    abstract fun solanaBalanceDao(): SolanaBalanceDao
    abstract fun solanaTransactionDao(): SolanaTransactionDao

    // Solana SPL Token DAO
    abstract fun splTokenDao(): SPLTokenDao

    // EVM DAOs
    abstract fun evmTokenDao(): EVMTokenDao
    abstract fun evmBalanceDao(): EVMBalanceDao
    abstract fun evmTransactionDao(): EVMTransactionDao

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
//                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}