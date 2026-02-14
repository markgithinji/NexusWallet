package com.example.nexuswallet.feature.coin.bitcoin


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BitcoinTransactionDao {

    @Insert
    suspend fun insert(transaction: BitcoinTransactionEntity)

    @Update
    suspend fun update(transaction: BitcoinTransactionEntity)

    @Query("SELECT * FROM BitcoinTransaction WHERE id = :id")
    suspend fun getById(id: String): BitcoinTransactionEntity?

    @Query("SELECT * FROM BitcoinTransaction WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWalletId(walletId: String): Flow<List<BitcoinTransactionEntity>>

    @Query("SELECT * FROM BitcoinTransaction WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<BitcoinTransactionEntity>

    @Query("DELETE FROM BitcoinTransaction WHERE id = :id")
    suspend fun deleteById(id: String)
}