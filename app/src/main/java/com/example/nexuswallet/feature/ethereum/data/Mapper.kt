package com.example.nexuswallet.feature.ethereum.data

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

private const val WEI_PER_ETH = "1000000000000000000"
private const val WEI_PER_GWEI = 1_000_000_000L
private const val GAS_LIMIT_STANDARD = 21000L

fun com.example.nexuswallet.feature.ethereum.data.remote.model.EtherscanTransactionResponse.toNativeETHTransaction(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String? = null
): com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction {
    val weiAmount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val ethAmount = weiAmount.divide(
        BigDecimal(_root_ide_package_.com.example.nexuswallet.feature.ethereum.data.WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val gasPriceWei = gasPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gasPriceGwei = gasPriceWei.divide(
        BigDecimal(_root_ide_package_.com.example.nexuswallet.feature.ethereum.data.WEI_PER_GWEI),
        6,
        RoundingMode.HALF_UP
    )

    val gasUsedValue = gasUsed.toLongOrNull() ?: 0L
    val feeWei = gasPriceWei.multiply(BigDecimal(gasUsedValue))
    val feeEth = feeWei.divide(
        BigDecimal(_root_ide_package_.com.example.nexuswallet.feature.ethereum.data.WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val isIncoming = to.equals(walletAddress, ignoreCase = true)
    val status = when {
        isError == "1" -> TransactionStatus.FAILED
        receiptStatus == "1" -> TransactionStatus.SUCCESS
        else -> TransactionStatus.PENDING
    }

    return _root_ide_package_.com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction(
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
        gasLimit = gas.toLongOrNull()
            ?: _root_ide_package_.com.example.nexuswallet.feature.ethereum.data.GAS_LIMIT_STANDARD,
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
fun List<com.example.nexuswallet.feature.ethereum.data.remote.model.EtherscanTransactionResponse>.toNativeETHTransactionList(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String? = null
): List<com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction> {
    return this.map { tx ->
        tx.toNativeETHTransaction(walletId, network, walletAddress, tokenExternalId)
    }
}

/**
 * Maps token transaction to domain model
 */
fun com.example.nexuswallet.feature.usdc.domain.TokenTransactionResponse.toTokenTransaction(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String
): com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction {
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
        BigDecimal(_root_ide_package_.com.example.nexuswallet.feature.ethereum.data.WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    return _root_ide_package_.com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction(
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
            BigDecimal(_root_ide_package_.com.example.nexuswallet.feature.ethereum.data.WEI_PER_GWEI),
            6,
            RoundingMode.HALF_UP
        ).toPlainString(),
        gasLimit = gas.toLongOrNull()
            ?: _root_ide_package_.com.example.nexuswallet.feature.ethereum.data.GAS_LIMIT_STANDARD,
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
fun List<com.example.nexuswallet.feature.usdc.domain.TokenTransactionResponse>.toTokenTransactionList(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    tokenExternalId: String
): List<com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction> {
    return this.map { tx ->
        tx.toTokenTransaction(walletId, network, walletAddress, tokenExternalId)
    }
}

/**
 * Maps EVMTransactionEntity to EVMTransaction
 */
fun com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionEntity.toDomain(): com.example.nexuswallet.feature.ethereum.domain.model.EVMTransaction {
    return if (tokenContract == null) {
        _root_ide_package_.com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction(
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
        _root_ide_package_.com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction(
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
            tokenContract = tokenContract,
            tokenSymbol = tokenSymbol!!,
            tokenDecimals = tokenDecimals!!,
            data = data,
            tokenExternalId = tokenExternalId
                ?: throw IllegalStateException("Token transaction missing tokenExternalId")
        )
    }
}

fun com.example.nexuswallet.feature.ethereum.domain.model.EVMTransaction.toEntity(): com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionEntity {
    return when (this) {
        is com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction -> _root_ide_package_.com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionEntity(
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

        is com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction -> _root_ide_package_.com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionEntity(
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