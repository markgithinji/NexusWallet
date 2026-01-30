package com.example.nexuswallet.feature.wallet.data.model

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "send_transactions")
data class SendTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val transactionJson: String,
    val timestamp: Long,
    val status: String
)

@Dao
interface SendTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SendTransactionEntity)

    @Query("SELECT * FROM send_transactions WHERE id = :id")
    suspend fun get(id: String): SendTransactionEntity?

    @Query("SELECT * FROM send_transactions WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWallet(walletId: String): Flow<List<SendTransactionEntity>>

    @Query("SELECT * FROM send_transactions WHERE status = :status")
    suspend fun getByStatus(status: String): List<SendTransactionEntity>

    @Query("DELETE FROM send_transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM send_transactions WHERE walletId = :walletId")
    suspend fun deleteByWallet(walletId: String)
}