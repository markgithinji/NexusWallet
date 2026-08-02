package com.example.nexuswallet.feature.ethereum.data

import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionEntity
import com.example.nexuswallet.feature.ethereum.data.remote.model.EtherscanTransactionDto
import com.example.nexuswallet.feature.ethereum.data.remote.model.TokenTransactionResponse
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransactionType
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.DEFAULT_TOKEN_GAS_LIMIT
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GAS_LIMIT_STANDARD
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GWEI_TO_WEI
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.WEI_PER_ETH
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Maps native ETH transaction response to domain model
 */
fun EtherscanTransactionDto.toNativeETHTransaction(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    evmTokenType: EVMTokenType = EVMTokenType.NATIVE
): NativeETHTransaction {
    val weiAmount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val ethAmount = weiAmount.divide(
        BigDecimal(WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val gasPriceWei = gasPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gasPriceGwei = gasPriceWei.divide(
        BigDecimal(GWEI_TO_WEI),
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
        receiptStatus == "0" -> TransactionStatus.FAILED
        else -> TransactionStatus.SUCCESS
    }

    return NativeETHTransaction(
        id = hash,
        walletId = walletId,
        fromAddress = from,
        toAddress = to,
        status = status,
        timestamp = timestamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
        note = null,
        feeLevel = FeeLevel.NORMAL,
        network = network,
        isIncoming = isIncoming,
        txHash = hash,
        amount = ethAmount.toPlainString(),  // Human readable ETH amount
        fee = feeEth.toPlainString(),        // Human readable fee in ETH
        symbol = evmTokenType.symbol,
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
        transactionType = EVMTransactionType.NATIVE_ETH,
        evmTokenType = evmTokenType,
        data = input
    )
}

/**
 * Maps list of API transactions to domain models (Native ETH)
 */
fun List<EtherscanTransactionDto>.toNativeETHTransactionList(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    evmTokenType: EVMTokenType = EVMTokenType.NATIVE
): List<NativeETHTransaction> {
    return this.map { tx ->
        tx.toNativeETHTransaction(walletId, network, walletAddress, evmTokenType)
    }
}
/**
 * Maps token transaction to domain model
 */
fun TokenTransactionResponse.toTokenTransaction(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    evmTokenType: EVMTokenType
): TokenTransaction {
    val weiAmount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val decimals = tokenDecimal.toIntOrNull() ?: evmTokenType.decimals
    val divisor = BigDecimal.TEN.pow(decimals)
    val tokenAmount = weiAmount.divide(divisor, decimals, RoundingMode.HALF_UP)

    val isIncoming = to.equals(walletAddress, ignoreCase = true)

    val status = when {
        // Etherscan uses isError="1" for internal failures
        // For ERC-20 transfers, txreceipt_status might not always be present or reliable 
        // depending on the network, but usually status="1" is success.
        // If it's 0, it failed.
        // For token transfers, we also check if 'value' is actually zero if it was meant to be a transfer
        // but here we just follow the status code.
        (this as? EtherscanTransactionDto)?.receiptStatus == "0" -> TransactionStatus.FAILED
        (this as? EtherscanTransactionDto)?.isError == "1" -> TransactionStatus.FAILED
        else -> TransactionStatus.SUCCESS
    }

    // Calculate gas fee
    val gasPriceWei = gasPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gasUsedValue = gasUsed.toLongOrNull() ?: 0L
    val feeWei = gasPriceWei.multiply(BigDecimal(gasUsedValue))
    val feeEth = feeWei.divide(
        BigDecimal(WEI_PER_ETH),
        18,
        RoundingMode.HALF_UP
    )

    val gasLimitValue = gas.toLongOrNull() ?: DEFAULT_TOKEN_GAS_LIMIT

    return TokenTransaction(
        id = hash,
        walletId = walletId,
        fromAddress = from,
        toAddress = to,
        status = status,
        timestamp = timeStamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
        note = "${evmTokenType.displayName} (${evmTokenType.symbol})${if (network.isTestnet) " - Testnet" else ""}",
        feeLevel = FeeLevel.NORMAL,
        network = network,
        isIncoming = isIncoming,
        txHash = hash,
        amount = tokenAmount.toPlainString(),  // Human readable token amount
        fee = feeEth.toPlainString(),          // Fee in ETH
        symbol = evmTokenType.symbol,
        amountWei = value,
        gasPriceWei = gasPrice,
        gasPriceGwei = gasPriceWei.divide(
            BigDecimal(GWEI_TO_WEI),
            6,
            RoundingMode.HALF_UP
        ).toPlainString(),
        gasLimit = gasLimitValue,
        feeWei = feeWei.toPlainString(),
        feeEth = feeEth.toPlainString(),
        nonce = nonce.toIntOrNull() ?: 0,
        chainId = network.chainId.toLong(),
        signedHex = null,
        transactionType = EVMTransactionType.TOKEN,
        evmTokenType = evmTokenType,
        tokenContract = contractAddress,
        data = input
    )
}

/**
 * Maps list of token transactions to domain models
 */
fun List<TokenTransactionResponse>.toTokenTransactionList(
    walletId: String,
    network: EthereumNetwork,
    walletAddress: String,
    evmTokenType: EVMTokenType
): List<TokenTransaction> {
    return this.map { tx ->
        tx.toTokenTransaction(walletId, network, walletAddress, evmTokenType)
    }
}

fun EVMTransactionEntity.toDomain(): EVMTransaction {
    return when (transactionType) {
        EVMTransactionType.NATIVE_ETH -> {
            val EVMTokenTypeValue = evmTokenType ?: EVMTokenType.NATIVE
            NativeETHTransaction(
                id = id,
                walletId = walletId,
                fromAddress = fromAddress,
                toAddress = toAddress,
                status = TransactionStatus.valueOf(status),
                timestamp = timestamp,
                note = note,
                feeLevel = FeeLevel.valueOf(feeLevel),
                network = network,
                isIncoming = isIncoming,
                txHash = txHash,
                amount = amountDecimal,  // ETH amount
                fee = feeEth,            // Fee in ETH
                symbol = EVMTokenTypeValue.symbol,
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
                transactionType = EVMTransactionType.NATIVE_ETH,
                evmTokenType = EVMTokenTypeValue,
                data = data
            )
        }

        EVMTransactionType.TOKEN -> {
            val EVMTokenTypeValue = evmTokenType ?: EVMTokenType.USDC
            TokenTransaction(
                id = id,
                walletId = walletId,
                fromAddress = fromAddress,
                toAddress = toAddress,
                status = TransactionStatus.valueOf(status),
                timestamp = timestamp,
                note = note,
                feeLevel = FeeLevel.valueOf(feeLevel),
                network = network,
                isIncoming = isIncoming,
                txHash = txHash,
                amount = amountDecimal,  // Token amount
                fee = feeEth,            // Fee in ETH
                symbol = EVMTokenTypeValue.symbol,
                amountWei = amountWei,
                gasPriceWei = gasPriceWei,
                gasPriceGwei = gasPriceGwei,
                gasLimit = gasLimit,
                feeWei = feeWei,
                feeEth = feeEth,
                nonce = nonce,
                chainId = chainId,
                signedHex = signedHex,
                transactionType = EVMTransactionType.TOKEN,
                evmTokenType = EVMTokenTypeValue,
                tokenContract = tokenContract ?: "",
                data = data
            )
        }
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
            evmTokenType = EVMTokenType.NATIVE,
            tokenContract = null,
            transactionType = EVMTransactionType.NATIVE_ETH
        )

        is TokenTransaction -> EVMTransactionEntity(
            id = id,
            walletId = walletId,
            fromAddress = fromAddress,
            toAddress = toAddress,
            amountWei = amountWei,
            amountDecimal = amount,
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
            evmTokenType = evmTokenType,
            tokenContract = tokenContract,
            transactionType = EVMTransactionType.TOKEN
        )
    }
}