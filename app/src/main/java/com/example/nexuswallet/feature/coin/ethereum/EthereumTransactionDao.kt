package com.example.nexuswallet.feature.coin.ethereum

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EthereumTransactionDao {

    @Insert
    suspend fun insert(transaction: EthereumTransactionEntity)

    @Update
    suspend fun update(transaction: EthereumTransactionEntity)

    @Query("SELECT * FROM EthereumTransaction WHERE id = :id")
    suspend fun getById(id: String): EthereumTransactionEntity?

    @Query("SELECT * FROM EthereumTransaction WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWalletId(walletId: String): Flow<List<EthereumTransactionEntity>>

    @Query("SELECT * FROM EthereumTransaction WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<EthereumTransactionEntity>

    @Query("DELETE FROM EthereumTransaction WHERE id = :id")
    suspend fun deleteById(id: String)
}