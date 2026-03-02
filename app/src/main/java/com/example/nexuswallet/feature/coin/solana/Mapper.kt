package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

private const val LAMPORTS_PER_SOL = 1_000_000_000L
private const val SOL_DECIMALS = 9
private const val DUST_THRESHOLD = 10_000L

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
        signature = signature,
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime,
        tokenMint = null,  // Native SOL transaction
        tokenSymbol = null,
        tokenDecimals = null
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
        "finalized", "confirmed" -> TransactionStatus.SUCCESS
        "processed" -> TransactionStatus.PENDING
        else -> TransactionStatus.PENDING
    }

    return SolanaTransaction(
        id = "sol_sig_${signature}_${System.currentTimeMillis()}",
        walletId = walletId,
        fromAddress = "",
        toAddress = "",
        status = status,
        timestamp = timestamp,
        note = null,
        feeLevel = FeeLevel.NORMAL,
        amountLamports = 0,
        amountSol = "0",
        feeLamports = 0,
        feeSol = "0",
        signature = signature,
        network = network,
        isIncoming = false,
        slot = slot,
        blockTime = blockTime,
        tokenMint = null,
        tokenSymbol = null,
        tokenDecimals = null
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
//
///**
// * Map SPL token transfer to domain transaction
// */
//fun SPLTokenTransferResponse.toDomainTransaction(
//    walletId: String,
//    walletAddress: String,
//    network: SolanaNetwork,
//    tokenInfo: SPLToken
//): SolanaTransaction {
//    val isIncoming = destination == walletAddress
//    val amount = amount.toLongOrNull() ?: 0
//    val amountDecimal = BigDecimal(amount).divide(
//        BigDecimal.TEN.pow(tokenInfo.decimals),
//        tokenInfo.decimals,
//        RoundingMode.HALF_UP
//    ).toPlainString()
//
//    return SolanaTransaction(
//        id = "spl_${signature}_${System.currentTimeMillis()}",
//        walletId = walletId,
//        fromAddress = source,
//        toAddress = destination,
//        status = TransactionStatus.SUCCESS,
//        timestamp = blockTime?.times(1000) ?: System.currentTimeMillis(),
//        note = "${tokenInfo.name} Transfer",
//        feeLevel = FeeLevel.NORMAL,
//        amountLamports = 0,  // Not applicable for SPL
//        amountSol = "0",      // Not applicable for SPL
//        feeLamports = fee ?: 0,
//        feeSol = fee?.let {
//            BigDecimal(it).divide(
//                BigDecimal(LAMPORTS_PER_SOL),
//                SOL_DECIMALS,
//                RoundingMode.HALF_UP
//            ).toPlainString()
//        } ?: "0",
//        signature = signature,
//        network = network,
//        isIncoming = isIncoming,
//        slot = slot,
//        blockTime = blockTime,
//        tokenMint = mint,
//        tokenSymbol = tokenInfo.symbol,
//        tokenDecimals = tokenInfo.decimals
//    )
//}

// ===== Entity Mappers =====

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
        signature = signature,
        network = network.toSolanaNetwork(),
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime,
        tokenMint = tokenMint,
        tokenSymbol = tokenSymbol,
        tokenDecimals = tokenDecimals
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
        signature = signature,
        network = network.toStorageString(),
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime,
        tokenMint = tokenMint,
        tokenSymbol = tokenSymbol,
        tokenDecimals = tokenDecimals
    )
}

// ===== Network Helpers =====

fun SolanaNetwork.toStorageString(): String = when (this) {
    SolanaNetwork.Mainnet -> "Mainnet"
    SolanaNetwork.Devnet -> "Devnet"
}

fun String.toSolanaNetwork(): SolanaNetwork = when (this) {
    "Mainnet" -> SolanaNetwork.Mainnet
    "Devnet" -> SolanaNetwork.Devnet
    else -> SolanaNetwork.Devnet
}