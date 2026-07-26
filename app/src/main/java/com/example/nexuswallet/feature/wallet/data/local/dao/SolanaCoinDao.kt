package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface SolanaCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: SolanaCoinEntity)

    @Update
    suspend fun update(coin: SolanaCoinEntity)

    @Query("SELECT * FROM solana_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<SolanaCoinEntity>

    @Query("SELECT * FROM solana_coins WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<List<SolanaCoinEntity>>

    @Query("SELECT * FROM solana_coins")
    fun observeAll(): Flow<List<SolanaCoinEntity>>

    @Query("DELETE FROM solana_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM solana_coins WHERE address = :address AND network = :network")
    suspend fun getByAddressAndNetwork(address: String, network: SolanaNetwork): SolanaCoinEntity?

    @Query("SELECT * FROM solana_coins WHERE address = :address")
    suspend fun getByAddress(address: String): SolanaCoinEntity?

    @Query("SELECT * FROM solana_coins WHERE id = :id")
    suspend fun getById(id: String): SolanaCoinEntity?
}