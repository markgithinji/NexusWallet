package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "address_book")
data class AddressBookEntryEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val address: String,
    val chain: String,
    val createdAt: Long
)
