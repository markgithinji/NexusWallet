package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.AddressBookEntryEntity
import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry

fun AddressBookEntryEntity.toDomain(): AddressBookEntry = AddressBookEntry(
    id = id,
    alias = alias,
    address = address,
    chain = chain,
    createdAt = createdAt
)

fun AddressBookEntry.toEntity(): AddressBookEntryEntity = AddressBookEntryEntity(
    id = id,
    alias = alias,
    address = address,
    chain = chain,
    createdAt = createdAt
)
