package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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
