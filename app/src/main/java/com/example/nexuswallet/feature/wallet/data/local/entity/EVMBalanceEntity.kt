package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import java.util.UUID

@Entity(
    tableName = "evm_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EVMTokenEntity::class,
            parentColumns = ["id"],
            childColumns = ["tokenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["walletId", "tokenId"], unique = true),
        Index(value = ["tokenId"])
    ]
)
data class EVMBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val tokenId: String,
    val externalTokenId: String,
    val address: String,
    val balanceWei: String,
    val balanceDecimal: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)