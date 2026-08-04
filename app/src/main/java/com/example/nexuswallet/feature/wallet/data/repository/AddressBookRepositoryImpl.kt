package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.data.local.dao.AddressBookDao
import com.example.nexuswallet.feature.wallet.data.local.mapper.toDomain
import com.example.nexuswallet.feature.wallet.data.local.mapper.toEntity
import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry
import com.example.nexuswallet.feature.wallet.domain.repository.AddressBookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddressBookRepositoryImpl @Inject constructor(
    private val addressBookDao: AddressBookDao
) : AddressBookRepository {

    override fun getAllEntries(): Flow<List<AddressBookEntry>> =
        addressBookDao.getAllEntries().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getEntriesForChain(chain: String): Flow<List<AddressBookEntry>> =
        addressBookDao.getEntriesForChain(chain).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addEntry(entry: AddressBookEntry) {
        addressBookDao.insertEntry(entry.toEntity())
    }

    override suspend fun deleteEntry(entry: AddressBookEntry) {
        addressBookDao.deleteEntry(entry.toEntity())
    }

    override suspend fun deleteEntryById(id: String) {
        addressBookDao.deleteEntryById(id)
    }
}
