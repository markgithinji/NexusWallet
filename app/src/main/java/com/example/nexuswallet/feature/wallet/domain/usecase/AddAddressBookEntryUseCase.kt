package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry
import com.example.nexuswallet.feature.wallet.domain.repository.AddressBookRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddAddressBookEntryUseCase @Inject constructor(
    private val repository: AddressBookRepository
) {
    suspend operator fun invoke(alias: String, address: String, chain: String) {
        val entry = AddressBookEntry(
            id = java.util.UUID.randomUUID().toString(),
            alias = alias,
            address = address,
            chain = chain
        )
        repository.addEntry(entry)
    }
}
