package com.example.nexuswallet.feature.coin.ethereum

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface EVMTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: EVMTransactionEntity)

    @Update
    suspend fun update(transaction: EVMTransactionEntity)

    @Query("SELECT * FROM evm_transactions WHERE id = :id")
    suspend fun getById(id: String): EVMTransactionEntity?

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWalletId(walletId: String): Flow<List<EVMTransactionEntity>>

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId AND tokenContract = :tokenContract ORDER BY timestamp DESC")
    fun getByWalletIdAndToken(walletId: String, tokenContract: String?): Flow<List<EVMTransactionEntity>>

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId AND tokenExternalId = :tokenExternalId ORDER BY timestamp DESC")
    fun getByWalletIdAndTokenExternalId(walletId: String, tokenExternalId: String): Flow<List<EVMTransactionEntity>>

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId AND tokenContract IS NULL ORDER BY timestamp DESC")
    fun getNativeTransactions(walletId: String): Flow<List<EVMTransactionEntity>>

    @Query("SELECT * FROM evm_transactions WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<EVMTransactionEntity>

    @Query("SELECT * FROM evm_transactions WHERE status = 'PENDING'")
    fun observePendingTransactions(): Flow<List<EVMTransactionEntity>>

    @Query("DELETE FROM evm_transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM evm_transactions WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_transactions WHERE walletId = :walletId AND tokenExternalId = :tokenExternalId")
    suspend fun deleteByWalletIdAndTokenExternalId(walletId: String, tokenExternalId: String)

    @Query("UPDATE evm_transactions SET status = :status WHERE id = :transactionId")
    suspend fun updateStatus(transactionId: String, status: String)

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId AND tokenExternalId = :tokenExternalId ORDER BY timestamp DESC")
    suspend fun getTransactionsForToken(walletId: String, tokenExternalId: String): List<EVMTransactionEntity>
}