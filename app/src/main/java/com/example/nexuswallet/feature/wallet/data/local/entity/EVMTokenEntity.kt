package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import java.util.UUID

@Entity(
    tableName = "evm_tokens",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["walletId"]),
        Index(value = ["walletId", "contractAddress", "network"], unique = true),
        Index(value = ["externalId"])
    ]
)
data class EVMTokenEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: String,
    val contractAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val tokenType: String,
    val externalId: String,
    val updatedAt: Long = System.currentTimeMillis()
)