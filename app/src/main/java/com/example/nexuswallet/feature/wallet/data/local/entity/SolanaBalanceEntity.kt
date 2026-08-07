package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

@Entity(
    tableName = "solana_balances",
    foreignKeys = [
        ForeignKey(
            entity = SolanaCoinEntity::class,
            parentColumns = ["id"],
            childColumns = ["coinId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["coinId"], unique = true)]
)
data class SolanaBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val coinId: String,
    val address: String,
    val lamports: String,
    val sol: String,
    val usdValue: BigDecimal,
    val updatedAt: Long = System.currentTimeMillis()
)
