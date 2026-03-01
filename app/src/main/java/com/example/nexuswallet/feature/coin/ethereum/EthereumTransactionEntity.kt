package com.example.nexuswallet.feature.coin.ethereum

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.local.WalletEntity
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
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
        Index(value = ["walletId", "tokenExternalId"])
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
    val amountDecimal: String,          // Human readable amount
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String,
    val data: String,
    val isIncoming: Boolean = false,
    val tokenContract: String? = null,
    val tokenSymbol: String? = null,
    val tokenDecimals: Int? = null,
    val tokenExternalId: String? = null,
    val transactionType: String,        // "NATIVE_ETH" or "TOKEN"
    val updatedAt: Long = System.currentTimeMillis()
)

fun EVMTransactionEntity.toDomain(): EVMTransaction {
    return if (tokenContract == null) {
        NativeETHTransaction(
            id = id,
            walletId = walletId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            status = TransactionStatus.valueOf(status),
            timestamp = timestamp,
            note = note,
            feeLevel = FeeLevel.valueOf(feeLevel),
            amountWei = amountWei,
            amountEth = amountDecimal,
            gasPriceWei = gasPriceWei,
            gasPriceGwei = gasPriceGwei,
            gasLimit = gasLimit,
            feeWei = feeWei,
            feeEth = feeEth,
            nonce = nonce,
            chainId = chainId,
            signedHex = signedHex,
            txHash = txHash,
            network = network,
            isIncoming = isIncoming,
            data = data,
            tokenExternalId = tokenExternalId
        )
    } else {
        TokenTransaction(
            id = id,
            walletId = walletId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            status = TransactionStatus.valueOf(status),
            timestamp = timestamp,
            note = note,
            feeLevel = FeeLevel.valueOf(feeLevel),
            amountWei = amountWei,
            amountDecimal = amountDecimal,
            gasPriceWei = gasPriceWei,
            gasPriceGwei = gasPriceGwei,
            gasLimit = gasLimit,
            feeWei = feeWei,
            feeEth = feeEth,
            nonce = nonce,
            chainId = chainId,
            signedHex = signedHex,
            txHash = txHash,
            network = network,
            isIncoming = isIncoming,
            tokenContract = tokenContract!!,
            tokenSymbol = tokenSymbol!!,
            tokenDecimals = tokenDecimals!!,
            data = data,
            tokenExternalId = tokenExternalId ?: throw IllegalStateException("Token transaction missing tokenExternalId")
        )
    }
}
fun EVMTransaction.toEntity(): EVMTransactionEntity {
    return when (this) {
        is NativeETHTransaction -> EVMTransactionEntity(
            id = id,
            walletId = walletId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            amountWei = amountWei,
            amountDecimal = amountEth,
            timestamp = timestamp,
            status = status.name,
            gasPriceWei = gasPriceWei,
            gasPriceGwei = gasPriceGwei,
            gasLimit = gasLimit,
            feeWei = feeWei,
            feeEth = feeEth,
            nonce = nonce,
            chainId = chainId,
            signedHex = signedHex,
            txHash = txHash,
            network = network,
            data = data,
            isIncoming = isIncoming,
            note = note,
            feeLevel = feeLevel.name,
            tokenExternalId = tokenExternalId,
            tokenSymbol = null,
            tokenDecimals = null,
            tokenContract = null,
            transactionType = "NATIVE_ETH"
        )

        is TokenTransaction -> EVMTransactionEntity(
            id = id,
            walletId = walletId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            amountWei = amountWei,
            amountDecimal = amountDecimal,
            timestamp = timestamp,
            status = status.name,
            gasPriceWei = gasPriceWei,
            gasPriceGwei = gasPriceGwei,
            gasLimit = gasLimit,
            feeWei = feeWei,
            feeEth = feeEth,
            nonce = nonce,
            chainId = chainId,
            signedHex = signedHex,
            txHash = txHash,
            network = network,
            data = data,
            isIncoming = isIncoming,
            note = note,
            feeLevel = feeLevel.name,
            tokenExternalId = tokenExternalId,
            tokenSymbol = tokenSymbol,
            tokenDecimals = tokenDecimals,
            tokenContract = tokenContract,
            transactionType = "TOKEN"
        )
    }
}