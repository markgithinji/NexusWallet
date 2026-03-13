package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BitcoinBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: BitcoinBalanceEntity)

    @Update
    suspend fun update(balance: BitcoinBalanceEntity)

    @Query("SELECT * FROM bitcoin_balances WHERE coinId IN (SELECT id FROM bitcoin_coins WHERE walletId = :walletId)")
    suspend fun getByWalletId(walletId: String): List<BitcoinBalanceEntity>
}
