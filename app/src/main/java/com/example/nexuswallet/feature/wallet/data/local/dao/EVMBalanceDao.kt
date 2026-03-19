package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork

@Dao
interface EVMBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: EVMBalanceEntity)

    @Update
    suspend fun update(balance: EVMBalanceEntity)

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<EVMBalanceEntity>

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND evmTokenType = :evmTokenType AND network = :network")
    suspend fun getBalance(
        walletId: String,
        evmTokenType: EVMTokenType,
        network: EthereumNetwork
    ): EVMBalanceEntity?

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND evmTokenType = :evmTokenType")
    suspend fun getBalancesByTokenType(
        walletId: String,
        evmTokenType: EVMTokenType
    ): List<EVMBalanceEntity>

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND network = :network")
    suspend fun getBalancesByNetwork(
        walletId: String,
        network: EthereumNetwork
    ): List<EVMBalanceEntity>

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId AND evmTokenType = :evmTokenType AND network = :network")
    suspend fun deleteBalance(
        walletId: String,
        evmTokenType: EVMTokenType,
        network: EthereumNetwork
    )

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId AND evmTokenType = :evmTokenType")
    suspend fun deleteBalancesByTokenType(
        walletId: String,
        evmTokenType: EVMTokenType
    )

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId AND network = :network")
    suspend fun deleteBalancesByNetwork(
        walletId: String,
        network: EthereumNetwork
    )

    @Query("SELECT COUNT(*) FROM evm_balances WHERE walletId = :walletId")
    suspend fun getBalanceCount(walletId: String): Int

    @Query("SELECT SUM(usdValue) FROM evm_balances WHERE walletId = :walletId")
    suspend fun getTotalUsdValue(walletId: String): Double?
}