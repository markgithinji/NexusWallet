package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionDao
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionEntity
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionDao
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionEntity
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionDao
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionEntity
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionDao
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionEntity
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Converters
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoinDao

@Database(
    entities = [
        // Wallet and coins
        WalletEntity::class,
        BitcoinCoinEntity::class,
        EthereumCoinEntity::class,
        SolanaCoinEntity::class,
        USDCCoinEntity::class,

        // Balances
        BitcoinBalanceEntity::class,
        EthereumBalanceEntity::class,
        SolanaBalanceEntity::class,
        USDCBalanceEntity::class,

        // Transactions
        BitcoinTransactionEntity::class,
        EthereumTransactionEntity::class,
        SolanaTransactionEntity::class,
        USDCTransactionEntity::class
    ],
    version = 13,  // From 12 to 13
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WalletDatabase : RoomDatabase() {
    // Wallet DAOs
    abstract fun walletDao(): WalletDao
    abstract fun bitcoinCoinDao(): BitcoinCoinDao
    abstract fun ethereumCoinDao(): EthereumCoinDao
    abstract fun solanaCoinDao(): SolanaCoinDao
    abstract fun usdcCoinDao(): USDCCoinDao

    // Balance DAOs
    abstract fun bitcoinBalanceDao(): BitcoinBalanceDao
    abstract fun ethereumBalanceDao(): EthereumBalanceDao
    abstract fun solanaBalanceDao(): SolanaBalanceDao
    abstract fun usdcBalanceDao(): USDCBalanceDao

    // Transaction DAOs
    abstract fun bitcoinTransactionDao(): BitcoinTransactionDao
    abstract fun ethereumTransactionDao(): EthereumTransactionDao
    abstract fun solanaTransactionDao(): SolanaTransactionDao
    abstract fun usdcTransactionDao(): USDCTransactionDao

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
                    .addMigrations(
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}