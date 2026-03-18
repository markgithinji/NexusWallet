package com.example.nexuswallet.feature.ethereum.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransactionType
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import java.util.UUID

@Entity(
    tableName = "evm_transactions",
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
        Index(value = ["txHash"], unique = true),
        Index(value = ["walletId", "tokenType"]),
        Index(value = ["walletId", "transactionType"])
    ]
)
data class EVMTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
    val amountWei: String,
    val amountDecimal: String,
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: EthereumNetwork,
    val data: String,
    val isIncoming: Boolean = false,
    val tokenContract: String? = null,
    val tokenType: TokenType? = null,
    val transactionType: EVMTransactionType,
    val updatedAt: Long = System.currentTimeMillis()
)