package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork

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

    @Query("SELECT * FROM bitcoin_coins WHERE address = :address AND network = :network")
    suspend fun getByAddressAndNetwork(address: String, network: BitcoinNetwork): BitcoinCoinEntity?

    @Query("SELECT * FROM bitcoin_coins WHERE address = :address")
    suspend fun getByAddress(address: String): BitcoinCoinEntity?

    @Query("SELECT * FROM bitcoin_coins WHERE id = :id")
    suspend fun getById(id: String): BitcoinCoinEntity?
}