package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
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
        Index(value = ["walletId", "tokenType", "network"], unique = true)
    ]
)
data class EVMTokenEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: EthereumNetwork,
    val contractAddress: String,
    val tokenType: TokenType,
    val updatedAt: Long = System.currentTimeMillis()
)