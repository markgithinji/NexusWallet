package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionDao
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionEntity
import com.example.nexuswallet.feature.coin.ethereum.EVMTransactionDao
import com.example.nexuswallet.feature.coin.ethereum.EVMTransactionEntity
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionDao
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionEntity
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Converters
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao

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
        EVMTransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WalletDatabase : RoomDatabase() {
    // Wallet DAOs
    abstract fun walletDao(): WalletDao

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
                     .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}