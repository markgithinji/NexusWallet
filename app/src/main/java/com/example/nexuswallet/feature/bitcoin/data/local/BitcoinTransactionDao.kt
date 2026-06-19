package com.example.nexuswallet.feature.bitcoin.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface BitcoinTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: BitcoinTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<BitcoinTransactionEntity>)

    @Transaction
    suspend fun replaceTransactions(
        walletId: String,
        network: BitcoinNetwork,
        transactions: List<BitcoinTransactionEntity>
    ) {
        deleteByWalletIdAndNetwork(walletId, network)
        insertAll(transactions)
    }

    @Update
    suspend fun update(transaction: BitcoinTransactionEntity)

    @Query("SELECT * FROM BitcoinTransaction WHERE id = :id")
    suspend fun getById(id: String): BitcoinTransactionEntity?

    @Query("SELECT * FROM BitcoinTransaction WHERE walletId = :walletId AND network = :network ORDER BY timestamp DESC")
    fun getByWalletIdAndNetwork(
        walletId: String,
        network: BitcoinNetwork
    ): Flow<List<BitcoinTransactionEntity>>

    @Query("SELECT * FROM BitcoinTransaction WHERE walletId = :walletId AND network = :network ORDER BY timestamp DESC")
    suspend fun getByWalletIdAndNetworkSync(
        walletId: String,
        network: BitcoinNetwork
    ): List<BitcoinTransactionEntity>

    @Query("SELECT * FROM BitcoinTransaction WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<BitcoinTransactionEntity>

    @Query("DELETE FROM BitcoinTransaction WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM BitcoinTransaction WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM BitcoinTransaction WHERE walletId = :walletId AND network = :network")
    suspend fun deleteByWalletIdAndNetwork(
        walletId: String,
        network: BitcoinNetwork
    )
}