package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.SPLTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaCoinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BitcoinCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: BitcoinCoinEntity)

    @Update
    suspend fun update(coin: BitcoinCoinEntity)

    @Query("SELECT * FROM bitcoin_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<BitcoinCoinEntity>

    @Query("DELETE FROM bitcoin_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM bitcoin_coins WHERE address = :address")
    suspend fun getByAddress(address: String): BitcoinCoinEntity?

    @Query("SELECT * FROM bitcoin_coins WHERE id = :id")
    suspend fun getById(id: String): BitcoinCoinEntity?
}

@Dao
interface BitcoinBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: BitcoinBalanceEntity)

    @Update
    suspend fun update(balance: BitcoinBalanceEntity)

    @Query("SELECT * FROM bitcoin_balances WHERE coinId IN (SELECT id FROM bitcoin_coins WHERE walletId = :walletId)")
    suspend fun getByWalletId(walletId: String): List<BitcoinBalanceEntity>

    @Query("SELECT * FROM bitcoin_balances WHERE coinId IN (SELECT id FROM bitcoin_coins WHERE walletId = :walletId)")
    fun observeByWalletId(walletId: String): Flow<List<BitcoinBalanceEntity>>

    @Query("SELECT * FROM bitcoin_balances WHERE coinId = :coinId")
    suspend fun getByCoinId(coinId: String): BitcoinBalanceEntity?
}

// ============ SOLANA DAOS  ============

@Dao
interface SolanaCoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: SolanaCoinEntity)

    @Update
    suspend fun update(coin: SolanaCoinEntity)

    @Query("SELECT * FROM solana_coins WHERE walletId = :walletId")
    suspend fun getByWalletId(walletId: String): List<SolanaCoinEntity>

    @Query("DELETE FROM solana_coins WHERE walletId = :walletId")
    suspend fun deleteByWalletId(walletId: String)

    @Query("SELECT * FROM solana_coins WHERE address = :address")
    suspend fun getByAddress(address: String): SolanaCoinEntity?

    @Query("SELECT * FROM solana_coins WHERE id = :id")
    suspend fun getById(id: String): SolanaCoinEntity?
}

@Dao
interface SolanaBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: SolanaBalanceEntity)

    @Update
    suspend fun update(balance: SolanaBalanceEntity)

    @Query("""
        SELECT sb.* FROM solana_balances sb
        INNER JOIN solana_coins sc ON sb.coinId = sc.id
        WHERE sc.walletId = :walletId
    """)
    suspend fun getByWalletId(walletId: String): List<SolanaBalanceEntity>

    @Query("""
        SELECT sb.* FROM solana_balances sb
        INNER JOIN solana_coins sc ON sb.coinId = sc.id
        WHERE sc.walletId = :walletId
    """)
    fun observeByWalletId(walletId: String): Flow<List<SolanaBalanceEntity>>

    @Query("SELECT * FROM solana_balances WHERE coinId = :coinId")
    suspend fun getByCoinId(coinId: String): SolanaBalanceEntity?

    @Query("SELECT * FROM solana_balances WHERE coinId = :coinId")
    fun observeByCoinId(coinId: String): Flow<SolanaBalanceEntity?>

    @Query("DELETE FROM solana_balances WHERE coinId IN (SELECT id FROM solana_coins WHERE walletId = :walletId)")
    suspend fun deleteByWalletId(walletId: String)

    @Query("DELETE FROM solana_balances WHERE coinId = :coinId")
    suspend fun deleteByCoinId(coinId: String)
}
// ============ SPL TOKEN DAOS  ============

@Dao
interface SPLTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: SPLTokenEntity)

    @Update
    suspend fun update(token: SPLTokenEntity)

    @Query("SELECT * FROM spl_tokens WHERE solanaCoinId = :solanaCoinId")
    suspend fun getBySolanaCoinId(solanaCoinId: String): List<SPLTokenEntity>

    @Query("SELECT * FROM spl_tokens WHERE id = :tokenId")
    suspend fun getById(tokenId: String): SPLTokenEntity?

    @Query("DELETE FROM spl_tokens WHERE solanaCoinId = :solanaCoinId")
    suspend fun deleteBySolanaCoinId(solanaCoinId: String)

    @Query("DELETE FROM spl_tokens WHERE id = :tokenId")
    suspend fun deleteById(tokenId: String)
}

// ============ EVM TOKEN DAOS ============

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

// ============ EVM BALANCE DAOS  ============

@Dao
interface EVMBalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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