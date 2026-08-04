package com.example.nexuswallet.feature.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AddressBookEntry(
    val id: String,
    val alias: String,
    val address: String,
    val chain: String, // e.g., "Bitcoin", "Ethereum", "Solana"
    val createdAt: Long = System.currentTimeMillis()
)
