package com.example.nexuswallet.feature.solana.data

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionEntity
import com.example.nexuswallet.feature.solana.data.remote.model.HeliusTransactionResponse
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.domain.model.TransferInfo
import com.example.nexuswallet.feature.solana.util.SolanaConstants.LAMPORTS_PER_SOL
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus

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
        network = network,
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
        network = network,
        isIncoming = isIncoming,
        slot = slot,
        blockTime = blockTime,
        tokenMint = tokenMint,
        tokenSymbol = tokenSymbol,
        tokenDecimals = tokenDecimals
    )
}

fun HeliusTransactionResponse.toDomain(
    walletId: String,
    walletAddress: String,
    network: SolanaNetwork
): SolanaTransaction? {
    // Parse transfer info
    val transferInfo = parseTransferInfo(this, walletAddress) ?: return null

    // Check if this is a token transfer (has token transfers)
    val isTokenTransfer = tokenTransfers.isNotEmpty()

    if (isTokenTransfer) {
        // For now, skip token transactions
        return null
    }

    // Native SOL transfer
    return SolanaTransaction(
        id = signature,
        walletId = walletId,
        fromAddress = transferInfo.from,
        toAddress = transferInfo.to,
        status = if (transactionError == null) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
        timestamp = timestamp * 1000, // Convert to milliseconds
        note = description,
        feeLevel = FeeLevel.NORMAL,
        amountLamports = transferInfo.amount,
        amountSol = (transferInfo.amount.toDouble() / LAMPORTS_PER_SOL).toString(),
        feeLamports = fee,
        feeSol = (fee.toDouble() / LAMPORTS_PER_SOL).toString(),
        signature = signature,
        network = network,
        isIncoming = transferInfo.isIncoming,
        tokenMint = null,
        tokenSymbol = null,
        tokenDecimals = null,
        slot = slot,
        blockTime = timestamp
    )
}

private fun parseTransferInfo(
    transaction: HeliusTransactionResponse,
    walletAddress: String
): TransferInfo? {
    val nativeTransfer = transaction.nativeTransfers.find {
        it.fromUserAccount == walletAddress || it.toUserAccount == walletAddress
    } ?: return null

    val isIncoming = nativeTransfer.toUserAccount == walletAddress

    return TransferInfo(
        from = nativeTransfer.fromUserAccount,
        to = nativeTransfer.toUserAccount,
        amount = nativeTransfer.amount,
        isIncoming = isIncoming,
        fee = transaction.fee
    )
}