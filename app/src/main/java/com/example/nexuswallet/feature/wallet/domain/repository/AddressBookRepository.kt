package com.example.nexuswallet.feature.wallet.domain.repository

import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry
import kotlinx.coroutines.flow.Flow

interface AddressBookRepository {
    fun getAllEntries(): Flow<List<AddressBookEntry>>
    fun getEntriesForChain(chain: String): Flow<List<AddressBookEntry>>
    suspend fun addEntry(entry: AddressBookEntry)
    suspend fun deleteEntry(entry: AddressBookEntry)
    suspend fun deleteEntryById(id: String)
}
