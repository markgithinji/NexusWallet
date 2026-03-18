package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import java.util.UUID

@Entity(
    tableName = "evm_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["walletId", "evmTokenType", "network"], unique = true),
        Index(value = ["evmTokenType"]),
        Index(value = ["network"])
    ]
)
data class EVMBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val evmTokenType: EVMTokenType,
    val network: EthereumNetwork,
    val address: String,
    val balanceWei: String,
    val balanceDecimal: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)