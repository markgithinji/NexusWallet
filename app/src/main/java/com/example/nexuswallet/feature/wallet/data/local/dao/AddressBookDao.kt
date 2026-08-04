package com.example.nexuswallet.feature.wallet.data.local.dao

import androidx.room.*
import com.example.nexuswallet.feature.wallet.data.local.entity.AddressBookEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressBookDao {
    @Query("SELECT * FROM address_book ORDER BY alias ASC")
    fun getAllEntries(): Flow<List<AddressBookEntryEntity>>

    @Query("SELECT * FROM address_book WHERE chain = :chain ORDER BY alias ASC")
    fun getEntriesForChain(chain: String): Flow<List<AddressBookEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: AddressBookEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: AddressBookEntryEntity)

    @Query("DELETE FROM address_book WHERE id = :id")
    suspend fun deleteEntryById(id: String)
}
