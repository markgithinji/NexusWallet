package com.example.nexuswallet.feature.coin.solana

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface SolanaTransactionDao {
    @Insert
    suspend fun insert(transaction: SolanaTransactionEntity)

    @Update
    suspend fun update(transaction: SolanaTransactionEntity)

    @Query("SELECT * FROM SolanaTransaction WHERE id = :id")
    suspend fun getById(id: String): SolanaTransactionEntity?

    @Query("SELECT * FROM SolanaTransaction WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWalletId(walletId: String): Flow<List<SolanaTransactionEntity>>

    @Query("SELECT * FROM SolanaTransaction WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<SolanaTransactionEntity>

    @Query("DELETE FROM SolanaTransaction WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM SolanaTransaction WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}