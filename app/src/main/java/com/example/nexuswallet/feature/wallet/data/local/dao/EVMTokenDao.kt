package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork

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

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId AND evmTokenType = :evmTokenType AND network = :network")
    suspend fun getByTokenTypeAndNetwork(
        walletId: String,
        evmTokenType: EVMTokenType,
        network: EthereumNetwork
    ): EVMTokenEntity?

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId AND evmTokenType = :evmTokenType")
    suspend fun getByTokenType(
        walletId: String,
        evmTokenType: EVMTokenType
    ): List<EVMTokenEntity>

    @Query("SELECT * FROM evm_tokens WHERE walletId = :walletId AND network = :network")
    suspend fun getByNetwork(
        walletId: String,
        network: EthereumNetwork
    ): List<EVMTokenEntity>

    @Query("DELETE FROM evm_tokens WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_tokens WHERE walletId = :walletId AND evmTokenType = :evmTokenType AND network = :network")
    suspend fun deleteByTokenTypeAndNetwork(
        walletId: String,
        evmTokenType: EVMTokenType,
        network: EthereumNetwork
    )

    @Query("SELECT COUNT(*) FROM evm_tokens WHERE walletId = :walletId")
    suspend fun getTokenCount(walletId: String): Int
}