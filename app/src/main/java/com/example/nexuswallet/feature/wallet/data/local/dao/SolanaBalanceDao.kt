package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SolanaBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: SolanaBalanceEntity)

    @Update
    suspend fun update(balance: SolanaBalanceEntity)

    @Query("""
        SELECT sb.* FROM solana_balances sb
        INNER JOIN solana_coins sc ON sb.coinId = sc.id
        WHERE sc.walletId = :walletId
    """)
    suspend fun getByWalletId(walletId: String): List<SolanaBalanceEntity>

    @Query("""
        SELECT sb.* FROM solana_balances sb
        INNER JOIN solana_coins sc ON sb.coinId = sc.id
        WHERE sc.walletId = :walletId
    """)
    fun observeByWalletId(walletId: String): Flow<List<SolanaBalanceEntity>>

    @Query("SELECT * FROM solana_balances WHERE coinId = :coinId")
    suspend fun getByCoinId(coinId: String): SolanaBalanceEntity?

    @Query("SELECT * FROM solana_balances WHERE coinId = :coinId")
    fun observeByCoinId(coinId: String): Flow<SolanaBalanceEntity?>

    @Query("DELETE FROM solana_balances WHERE coinId IN (SELECT id FROM solana_coins WHERE walletId = :walletId)")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM solana_balances WHERE coinId = :coinId")
    suspend fun deleteByCoinId(coinId: String)
}