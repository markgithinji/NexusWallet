package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EVMBalanceDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(balance: EVMBalanceEntity)

    @Update
    suspend fun update(balance: EVMBalanceEntity)

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<EVMBalanceEntity>

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<List<EVMBalanceEntity>>

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND tokenId = :tokenId")
    suspend fun getBalance(walletId: String, tokenId: String): EVMBalanceEntity?

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND tokenId = :tokenId")
    fun observeBalance(walletId: String, tokenId: String): Flow<EVMBalanceEntity?>

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId AND tokenId = :tokenId")
    suspend fun deleteBalance(walletId: String, tokenId: String)
}