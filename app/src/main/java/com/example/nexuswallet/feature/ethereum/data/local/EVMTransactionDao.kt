package com.example.nexuswallet.feature.ethereum.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
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

    @Query("""
        SELECT * FROM evm_transactions 
        WHERE walletId = :walletId 
        AND tokenContract = :tokenContract 
        ORDER BY timestamp DESC
    """)
    fun getByWalletIdAndToken(
        walletId: String,
        tokenContract: String?
    ): Flow<List<EVMTransactionEntity>>

    @Query(
        """
        SELECT * FROM evm_transactions 
        WHERE walletId = :walletId 
        AND evmTokenType = :evmTokenType 
        ORDER BY timestamp DESC
    """
    )
    fun getByWalletIdAndTokenType(
        walletId: String,
        evmTokenType: EVMTokenType
    ): Flow<List<EVMTransactionEntity>>

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId AND evmTokenType IS NULL ORDER BY timestamp DESC")
    fun getNativeTransactions(walletId: String): Flow<List<EVMTransactionEntity>>

    @Query("SELECT * FROM evm_transactions WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<EVMTransactionEntity>

    @Query("SELECT * FROM evm_transactions WHERE status = 'PENDING'")
    fun observePendingTransactions(): Flow<List<EVMTransactionEntity>>

    @Query("DELETE FROM evm_transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM evm_transactions WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_transactions WHERE walletId = :walletId AND evmTokenType = :evmTokenType")
    suspend fun deleteByWalletIdAndTokenType(walletId: String, evmTokenType: EVMTokenType)

    @Query("UPDATE evm_transactions SET status = :status WHERE id = :transactionId")
    suspend fun updateStatus(transactionId: String, status: String)

    @Query(
        """
        SELECT * FROM evm_transactions 
        WHERE walletId = :walletId 
        AND evmTokenType = :evmTokenType 
        ORDER BY timestamp DESC
    """
    )
    suspend fun getTransactionsForTokenType(
        walletId: String,
        evmTokenType: EVMTokenType
    ): List<EVMTransactionEntity>

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId ORDER BY timestamp DESC")
    suspend fun getByWalletIdSync(walletId: String): List<EVMTransactionEntity>

    @Query("SELECT * FROM evm_transactions WHERE walletId = :walletId AND evmTokenType IS NULL ORDER BY timestamp DESC")
    suspend fun getNativeTransactionsSync(walletId: String): List<EVMTransactionEntity>
}