package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import java.util.UUID

@Entity(
    tableName = "solana_coins",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"])]
)
data class SolanaCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: String,
    val updatedAt: Long = System.currentTimeMillis()
)
