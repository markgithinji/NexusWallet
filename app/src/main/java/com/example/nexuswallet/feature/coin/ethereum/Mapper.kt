package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.TokenTransactionResponse
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

private const val WEI_PER_ETH = "1000000000000000000"
private const val WEI_PER_GWEI = 1_000_000_000L
private const val GAS_LIMIT_STANDARD = 21000L

fun EtherscanTransactionResponse.toNativeETHTransaction(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String? = null
): NativeETHTransaction {
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

    return NativeETHTransaction(
        id = "eth_tx_${System.currentTimeMillis()}_${hash.take(8)}",
        walletId = walletId,
        fromAddress = from,
        toAddress = to,
        status = status,
        timestamp = timestamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
        note = null,
        feeLevel = FeeLevel.NORMAL,
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
        isIncoming = isIncoming,
        data = input,
        tokenExternalId = tokenExternalId
    )
}

/**
 * Maps list of API transactions to domain models (Native ETH)
 */
fun List<EtherscanTransactionResponse>.toNativeETHTransactionList(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String? = null
): List<NativeETHTransaction> {
    return this.map { tx ->
        tx.toNativeETHTransaction(walletId, network, walletAddress, tokenExternalId)
    }
}

/**
 * Maps token transaction to domain model
 */
fun TokenTransactionResponse.toTokenTransaction(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String
): TokenTransaction {
    val weiAmount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val decimals = tokenDecimal.toIntOrNull() ?: 18
    val divisor = BigDecimal.TEN.pow(decimals)
    val tokenAmount = weiAmount.divide(divisor, decimals, RoundingMode.HALF_UP)

    val isIncoming = to.equals(walletAddress, ignoreCase = true)

    // Calculate gas fee
    val gasPriceWei = gasPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gasUsedValue = gasUsed.toLongOrNull() ?: 0L
    val feeWei = gasPriceWei.multiply(BigDecimal(gasUsedValue))
    val feeEth = feeWei.divide(
        BigDecimal(WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val generatedId = when (tokenSymbol) {
        "USDC" -> "${network.chainId}_usdc"
        "USDT" -> "${network.chainId}_usdt"
        else -> "${network.chainId}_${contractAddress}"
    }

    return TokenTransaction(
        id = "token_tx_${System.currentTimeMillis()}_${hash.take(8)}",
        walletId = walletId,
        fromAddress = from,
        toAddress = to,
        status = TransactionStatus.SUCCESS,
        timestamp = timeStamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
        note = "$tokenName ($tokenSymbol)",
        feeLevel = FeeLevel.NORMAL,
        amountWei = value,
        amountDecimal = tokenAmount.toPlainString(),
        gasPriceWei = gasPrice,
        gasPriceGwei = gasPriceWei.divide(
            BigDecimal(WEI_PER_GWEI),
            6,
            RoundingMode.HALF_UP
        ).toPlainString(),
        gasLimit = gas.toLongOrNull() ?: GAS_LIMIT_STANDARD,
        feeWei = feeWei.toPlainString(),
        feeEth = feeEth.toPlainString(),
        nonce = nonce.toIntOrNull() ?: 0,
        chainId = network.chainId.toLong(),
        signedHex = null,
        txHash = hash,
        network = network.displayName,
        isIncoming = isIncoming,
        tokenContract = contractAddress,
        tokenSymbol = tokenSymbol,
        tokenDecimals = decimals,
        data = input,
        tokenExternalId = tokenExternalId
    )
}

/**
 * Maps list of token transactions to domain models
 */
fun List<TokenTransactionResponse>.toTokenTransactionList(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String
): List<TokenTransaction> {
    return this.map { tx ->
        tx.toTokenTransaction(walletId, network, walletAddress, tokenExternalId)
    }
}