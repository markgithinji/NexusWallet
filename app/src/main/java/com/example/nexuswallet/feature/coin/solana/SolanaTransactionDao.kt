package com.example.nexuswallet.feature.coin.solana

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface SolanaTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: SolanaTransactionEntity)

    @Update
    suspend fun update(transaction: SolanaTransactionEntity)

    @Query("SELECT * FROM solana_transactions WHERE id = :id")
    suspend fun getById(id: String): SolanaTransactionEntity?

    @Query("SELECT * FROM solana_transactions WHERE walletId = :walletId AND network = :network ORDER BY timestamp DESC")
    fun getByWalletIdAndNetwork(walletId: String, network: String): Flow<List<SolanaTransactionEntity>>

    @Query("SELECT * FROM solana_transactions WHERE walletId = :walletId AND tokenMint = :tokenMint AND network = :network ORDER BY timestamp DESC")
    fun getByWalletIdTokenAndNetwork(walletId: String, tokenMint: String?, network: String): Flow<List<SolanaTransactionEntity>>

    @Query("SELECT * FROM solana_transactions WHERE walletId = :walletId AND tokenMint IS NULL AND network = :network ORDER BY timestamp DESC")
    fun getNativeTransactions(walletId: String, network: String): Flow<List<SolanaTransactionEntity>>

    @Query("SELECT * FROM solana_transactions WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getByWalletId(walletId: String): Flow<List<SolanaTransactionEntity>>

    @Query("SELECT * FROM solana_transactions WHERE status = 'PENDING'")
    fun observePendingTransactions(): Flow<List<SolanaTransactionEntity>>

    @Query("SELECT * FROM solana_transactions WHERE walletId = :walletId AND network = :network ORDER BY timestamp DESC")
    suspend fun getByWalletIdAndNetworkSync(walletId: String, network: String): List<SolanaTransactionEntity>

    @Query("SELECT * FROM solana_transactions WHERE walletId = :walletId AND tokenMint IS NULL AND network = :network ORDER BY timestamp DESC")
    suspend fun getNativeTransactionsSync(walletId: String, network: String): List<SolanaTransactionEntity>

    @Query("SELECT * FROM solana_transactions WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<SolanaTransactionEntity>

    @Query("DELETE FROM solana_transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM solana_transactions WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM solana_transactions WHERE walletId = :walletId AND network = :network")
    suspend fun deleteByWalletIdAndNetwork(walletId: String, network: String)

    @Query("UPDATE solana_transactions SET status = :status WHERE id = :transactionId")
    suspend fun updateStatus(transactionId: String, status: String)
}