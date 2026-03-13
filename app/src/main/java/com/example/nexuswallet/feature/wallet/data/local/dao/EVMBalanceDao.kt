package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EVMBalanceDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(balance: EVMBalanceEntity)

    @Update
    suspend fun update(balance: EVMBalanceEntity)

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<EVMBalanceEntity>

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}