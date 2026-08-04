package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry
import com.example.nexuswallet.feature.wallet.domain.repository.AddressBookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAddressBookEntriesUseCase @Inject constructor(
    private val addressBookRepository: AddressBookRepository
) {
    operator fun invoke(chain: String? = null): Flow<List<AddressBookEntry>> {
        return if (chain != null) {
            addressBookRepository.getEntriesForChain(chain)
        } else {
            addressBookRepository.getAllEntries()
        }
    }
}
