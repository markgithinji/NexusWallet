package com.example.nexuswallet.feature.solana.data

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus

fun com.example.nexuswallet.feature.solana.data.local.SolanaTransactionEntity.toDomain(): com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction {
    return _root_ide_package_.com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction(
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

fun com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction.toEntity(): com.example.nexuswallet.feature.solana.data.local.SolanaTransactionEntity {
    return _root_ide_package_.com.example.nexuswallet.feature.solana.data.local.SolanaTransactionEntity(
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

fun SolanaNetwork.toStorageString(): String = when (this) {
    SolanaNetwork.Mainnet -> SolanaNetwork.Mainnet.name
    SolanaNetwork.Devnet -> SolanaNetwork.Devnet.name
}

fun String.toSolanaNetwork(): SolanaNetwork = when (this) {
    SolanaNetwork.Mainnet.name -> SolanaNetwork.Mainnet
    SolanaNetwork.Devnet.name-> SolanaNetwork.Devnet
    else -> SolanaNetwork.Devnet
}