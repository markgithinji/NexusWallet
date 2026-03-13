package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "bitcoin_balances",
    foreignKeys = [
        ForeignKey(
            entity = BitcoinCoinEntity::class,
            parentColumns = ["id"],
            childColumns = ["coinId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["coinId"], unique = true)]
)
data class BitcoinBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val coinId: String,
    val address: String,
    val satoshis: String,
    val btc: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)