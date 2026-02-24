package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

private const val WEI_PER_ETH = "1000000000000000000"
private const val WEI_PER_GWEI = 1_000_000_000L
private const val GAS_LIMIT_STANDARD = 21000L

/**
 * Maps Etherscan API transaction to domain model
 */
fun EtherscanTransactionResponse.toDomain(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String
): EthereumTransaction {
    val weiAmount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val ethAmount = weiAmount.divide(
        BigDecimal(WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val gasPriceWei = gasPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gasPriceGwei = gasPriceWei.divide(
        BigDecimal(WEI_PER_GWEI),
        6,
        RoundingMode.HALF_UP
    )

    val gasUsedValue = gasUsed.toLongOrNull() ?: 0L
    val feeWei = gasPriceWei.multiply(BigDecimal(gasUsedValue))
    val feeEth = feeWei.divide(
        BigDecimal(WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val isIncoming = to.equals(walletAddress, ignoreCase = true)
    val status = when {
        isError == "1" -> TransactionStatus.FAILED
        receiptStatus == "1" -> TransactionStatus.SUCCESS
        else -> TransactionStatus.PENDING
    }

    return EthereumTransaction(
        id = "eth_tx_${System.currentTimeMillis()}_${hash.take(8)}",
        walletId = walletId,
        fromAddress = from,
        toAddress = to,
        status = status,
        timestamp = timestamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
        note = null,
        feeLevel = FeeLevel.NORMAL, // Default, actual level not provided by API
        amountWei = value,
        amountEth = ethAmount.toPlainString(),
        gasPriceWei = gasPrice,
        gasPriceGwei = gasPriceGwei.toPlainString(),
        gasLimit = gas.toLongOrNull() ?: GAS_LIMIT_STANDARD,
        feeWei = feeWei.toPlainString(),
        feeEth = feeEth.toPlainString(),
        nonce = nonce.toIntOrNull() ?: 0,
        chainId = network.chainId.toLong(),
        signedHex = null,
        txHash = hash,
        network = network.displayName,
        data = input,
        isIncoming = isIncoming
    )
}

/**
 * Maps list of API transactions to domain models
 */
fun List<EtherscanTransactionResponse>.toDomain(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String
): List<EthereumTransaction> {
    return this.map { tx ->
        tx.toDomain(walletId, network, walletAddress)
    }
}