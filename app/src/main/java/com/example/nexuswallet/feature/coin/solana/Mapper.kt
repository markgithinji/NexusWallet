package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

private const val LAMPORTS_PER_SOL = 1_000_000_000L
private const val SOL_DECIMALS = 9
private const val DUST_THRESHOLD = 10_000L

/**
 * Map SolanaTransactionDetailsResponse to domain transaction
 */
fun SolanaTransactionDetailsResponse.toDomainTransaction(
    walletId: String,
    walletAddress: String,
    network: SolanaNetwork,
    signature: String
): SolanaTransaction? {
    val meta = this.meta ?: return null

    // Skip failed transactions
    if (meta.err != null) return null

    val accountKeys = transaction.message.accountKeys

    // Find our wallet in the account keys
    val walletIndex = accountKeys.indexOfFirst { it == walletAddress }
    if (walletIndex < 0) return null

    // Calculate balance change
    val preBalance = meta.preBalances.getOrNull(walletIndex) ?: 0
    val postBalance = meta.postBalances.getOrNull(walletIndex) ?: 0
    val balanceChange = postBalance - preBalance

    // Skip if no change
    if (balanceChange == 0L) return null

    val isIncoming = balanceChange > 0
    val amount = kotlin.math.abs(balanceChange)

    // Find counterparty (the other address involved)
    var counterparty = ""
    accountKeys.forEachIndexed { index, key ->
        if (index != walletIndex) {
            val otherPre = meta.preBalances.getOrNull(index) ?: 0
            val otherPost = meta.postBalances.getOrNull(index) ?: 0
            val otherChange = otherPost - otherPre

            if (otherChange == -balanceChange ||
                (otherChange < 0 && otherChange > -balanceChange - DUST_THRESHOLD)
            ) {
                counterparty = key
            }
        }
    }

    // Format amounts
    val amountSol = BigDecimal(amount).divide(
        BigDecimal(LAMPORTS_PER_SOL),
        SOL_DECIMALS,
        RoundingMode.HALF_UP
    ).toPlainString()

    val feeSol = BigDecimal(meta.fee).divide(
        BigDecimal(LAMPORTS_PER_SOL),
        SOL_DECIMALS,
        RoundingMode.HALF_UP
    ).toPlainString()

    val timestamp = blockTime?.times(1000) ?: System.currentTimeMillis()

    return SolanaTransaction(
        id = "sol_${signature}_${System.currentTimeMillis()}",
        walletId = walletId,
        fromAddress = if (isIncoming) counterparty else walletAddress,
        toAddress = if (isIncoming) walletAddress else counterparty,
        status = if (meta.err == null) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
        timestamp = timestamp,
        note = null,
        feeLevel = FeeLevel.NORMAL,
        amountLamports = amount,
        amountSol = amountSol,
        feeLamports = meta.fee,
        feeSol = feeSol,
        blockhash = transaction.message.recentBlockhash,
        signature = signature,
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime
    )
}

/**
 * Map SolanaTransactionResponse (signature info) to a minimal transaction
 */
fun SolanaTransactionResponse.toDomainTransaction(
    walletId: String,
    network: SolanaNetwork
): SolanaTransaction {
    val timestamp = blockTime?.times(1000) ?: System.currentTimeMillis()

    val status = when (confirmationStatus?.lowercase()) {
        "finalized" -> TransactionStatus.SUCCESS
        "confirmed" -> TransactionStatus.SUCCESS
        "processed" -> TransactionStatus.PENDING
        else -> TransactionStatus.PENDING
    }

    return SolanaTransaction(
        id = "sol_sig_${signature}_${System.currentTimeMillis()}",
        walletId = walletId,
        fromAddress = "",  // Will be filled when details are available
        toAddress = "",    // Will be filled when details are available
        status = status,
        timestamp = timestamp,
        note = null,
        feeLevel = FeeLevel.NORMAL,
        amountLamports = 0,
        amountSol = "0",
        feeLamports = 0,
        feeSol = "0",
        blockhash = "",
        signature = signature,
        network = network,
        isIncoming = false,
        slot = slot,
        blockTime = blockTime
    )
}

/**
 * Map a pair of signature info and details to a complete transaction
 */
fun Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>.toDomainTransaction(
    walletId: String,
    walletAddress: String,
    network: SolanaNetwork
): SolanaTransaction? {
    val (sigInfo, details) = this

    // If we have details, use them with the signature from sigInfo
    if (details != null) {
        return details.toDomainTransaction(
            walletId = walletId,
            walletAddress = walletAddress,
            network = network,
            signature = sigInfo.signature
        )
    }

    // Otherwise create a minimal transaction from signature info
    return sigInfo.toDomainTransaction(walletId, network)
}

fun SolanaTransactionEntity.toDomain(): SolanaTransaction {
    return SolanaTransaction(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = TransactionStatus.valueOf(status),
        timestamp = timestamp,
        note = note,
        feeLevel = FeeLevel.valueOf(feeLevel),
        amountLamports = amountLamports,
        amountSol = amountSol,
        feeLamports = feeLamports,
        feeSol = feeSol,
        blockhash = blockhash,
        signedData = signedData,
        signature = signature,
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime
    )
}

fun SolanaTransaction.toEntity(): SolanaTransactionEntity {
    return SolanaTransactionEntity(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status.name,
        timestamp = timestamp,
        note = note,
        feeLevel = feeLevel.name,
        amountLamports = amountLamports,
        amountSol = amountSol,
        feeLamports = feeLamports,
        feeSol = feeSol,
        blockhash = blockhash,
        signedData = signedData,
        signature = signature,
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime
    )
}