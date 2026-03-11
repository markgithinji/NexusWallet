package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(wallet: WalletEntity)

    @Query("SELECT * FROM wallets WHERE id = :walletId")
    suspend fun get(walletId: String): WalletEntity?

    @Query("SELECT * FROM wallets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WalletEntity>>

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun delete(walletId: String)

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM wallets WHERE id = :walletId)")
    suspend fun exists(walletId: String): Boolean
}