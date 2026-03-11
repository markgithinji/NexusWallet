package com.example.nexuswallet.feature.coin.solana.data

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.data.local.SolanaTransactionEntity
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus

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

fun SolanaNetwork.toStorageString(): String = when (this) {
    SolanaNetwork.Mainnet -> SolanaNetwork.Mainnet.name
    SolanaNetwork.Devnet -> SolanaNetwork.Devnet.name
}

fun String.toSolanaNetwork(): SolanaNetwork = when (this) {
    SolanaNetwork.Mainnet.name -> SolanaNetwork.Mainnet
    SolanaNetwork.Devnet.name-> SolanaNetwork.Devnet
    else -> SolanaNetwork.Devnet
}