package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.EthereumBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.EthereumCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.USDCBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.USDCCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity)

    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("SELECT * FROM wallets WHERE id = :walletId")
    suspend fun get(walletId: String): WalletEntity?

    @Query("SELECT * FROM wallets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WalletEntity>>

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun delete(walletId: String)
}

@Dao
interface BitcoinCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: BitcoinCoinEntity)

    @Update
    suspend fun update(coin: BitcoinCoinEntity)

    @Query("SELECT * FROM bitcoin_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): BitcoinCoinEntity?

    @Query("DELETE FROM bitcoin_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}

@Dao
interface EthereumCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: EthereumCoinEntity)

    @Update
    suspend fun update(coin: EthereumCoinEntity)

    @Query("SELECT * FROM ethereum_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): EthereumCoinEntity?

    @Query("DELETE FROM ethereum_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}

@Dao
interface SolanaCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: SolanaCoinEntity)

    @Update
    suspend fun update(coin: SolanaCoinEntity)

    @Query("SELECT * FROM solana_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): SolanaCoinEntity?

    @Query("DELETE FROM solana_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}

@Dao
interface USDCCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: USDCCoinEntity)

    @Update
    suspend fun update(coin: USDCCoinEntity)

    @Query("SELECT * FROM usdc_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): USDCCoinEntity?

    @Query("DELETE FROM usdc_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}

@Dao
interface BitcoinBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: BitcoinBalanceEntity)

    @Update
    suspend fun update(balance: BitcoinBalanceEntity)

    @Query("SELECT * FROM bitcoin_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): BitcoinBalanceEntity?

    @Query("DELETE FROM bitcoin_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM bitcoin_balances WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<BitcoinBalanceEntity?>
}

@Dao
interface EthereumBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: EthereumBalanceEntity)

    @Update
    suspend fun update(balance: EthereumBalanceEntity)

    @Query("SELECT * FROM ethereum_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): EthereumBalanceEntity?

    @Query("DELETE FROM ethereum_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM ethereum_balances WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<EthereumBalanceEntity?>
}

@Dao
interface SolanaBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: SolanaBalanceEntity)

    @Update
    suspend fun update(balance: SolanaBalanceEntity)

    @Query("SELECT * FROM solana_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): SolanaBalanceEntity?

    @Query("DELETE FROM solana_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM solana_balances WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<SolanaBalanceEntity?>
}

@Dao
interface USDCBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: USDCBalanceEntity)

    @Update
    suspend fun update(balance: USDCBalanceEntity)

    @Query("SELECT * FROM usdc_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): USDCBalanceEntity?

    @Query("DELETE FROM usdc_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM usdc_balances WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<USDCBalanceEntity?>
}