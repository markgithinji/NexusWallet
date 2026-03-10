package com.example.nexuswallet.feature.coin.bitcoin.data.local
l
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BitcoinTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: BitcoinTransactionEntity)

    @Update
    suspend fun update(transaction: BitcoinTransactionEntity)

    @Query("SELECT * FROM BitcoinTransaction WHERE id = :id")
    suspend fun getById(id: String): BitcoinTransactionEntity?

    @Query("SELECT * FROM BitcoinTransaction WHERE walletId = :walletId AND network = :network ORDER BY timestamp DESC")
    fun getByWalletIdAndNetwork(walletId: String, network: String): Flow<List<BitcoinTransactionEntity>>

    @Query("SELECT * FROM BitcoinTransaction WHERE walletId = :walletId AND network = :network ORDER BY timestamp DESC")
    suspend fun getByWalletIdAndNetworkSync(walletId: String, network: String): List<BitcoinTransactionEntity>

    @Query("SELECT * FROM BitcoinTransaction WHERE status = 'PENDING'")
    suspend fun getPendingTransactions(): List<BitcoinTransactionEntity>

    @Query("DELETE FROM BitcoinTransaction WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM BitcoinTransaction WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM BitcoinTransaction WHERE walletId = :walletId AND network = :network")
    suspend fun deleteByWalletIdAndNetwork(walletId: String, network: String)
}