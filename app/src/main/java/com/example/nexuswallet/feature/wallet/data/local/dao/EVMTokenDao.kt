package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EVMTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: EVMTokenEntity)

    @Update
    suspend fun update(token: EVMTokenEntity)

    @Query("SELECT * FROM evm_tokens WHERE id = :tokenId")
    suspend fun getById(tokenId: String): EVMTokenEntity?

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<EVMTokenEntity>

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId AND externalId = :externalId")
    suspend fun getByExternalId(walletId: String, externalId: String): EVMTokenEntity?

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId")
    fun observeByWalletId(walletId: String): Flow<List<EVMTokenEntity>>

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId AND contractAddress = :contractAddress AND network = :network")
    suspend fun getToken(
        walletId: String,
        contractAddress: String,
        network: String
    ): EVMTokenEntity?

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId AND tokenType = :tokenType")
    suspend fun getByType(walletId: String, tokenType: String): List<EVMTokenEntity>

    @Query("DELETE FROM evm_tokens WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_tokens WHERE walletId = :walletId AND contractAddress = :contractAddress AND network = :network")
    suspend fun deleteToken(walletId: String, contractAddress: String, network: String)
}