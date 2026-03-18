package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
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

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND tokenType = :tokenType AND network = :network")
    suspend fun getBalance(
        walletId: String,
        tokenType: TokenType,
        network: EthereumNetwork
    ): EVMBalanceEntity?

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND tokenType = :tokenType")
    suspend fun getBalancesByTokenType(
        walletId: String,
        tokenType: TokenType
    ): List<EVMBalanceEntity>

    @Query("SELECT * FROM evm_balances WHERE walletId = :walletId AND network = :network")
    suspend fun getBalancesByNetwork(
        walletId: String,
        network: EthereumNetwork
    ): List<EVMBalanceEntity>

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId AND tokenType = :tokenType AND network = :network")
    suspend fun deleteBalance(
        walletId: String,
        tokenType: TokenType,
        network: EthereumNetwork
    )

    @Query("DELETE FROM evm_balances WHERE walletId = :walletId AND tokenType = :tokenType")
    suspend fun deleteBalancesByTokenType(
        walletId: String,
        tokenType: TokenType
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