package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaBalanceEntity
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

    @Query("SELECT * FROM solana_balances")
    fun observeAll(): Flow<List<SolanaBalanceEntity>>
}