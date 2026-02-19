package com.example.nexuswallet.feature.coin.usdc

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface USDCTransactionDao {
    @Insert
    suspend fun insert(transaction: USDCTransactionEntity)

    @Update
    suspend fun update(transaction: USDCTransactionEntity)

    @Query("SELECT * FROM USDCSendTransaction WHERE id = :id")
    suspend fun getById(id: String): USDCTransactionEntity?

    @Query("SELECT * FROM USDCSendTransaction WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWalletId(walletId: String): Flow<List<USDCTransactionEntity>>

    @Query("SELECT * FROM USDCSendTransaction WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<USDCTransactionEntity>

    @Query("DELETE FROM USDCSendTransaction WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM USDCSendTransaction WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}