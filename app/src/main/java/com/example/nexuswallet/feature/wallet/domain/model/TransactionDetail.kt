package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus

data class TransactionDetail(
    val id: String,
    val walletId: String,
    val coinType: CoinType,
    val network: String,
    val hash: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val fromAddress: String,
    val toAddress: String,
    val amount: String, // Raw amount
    val fee: String, // Raw fee
    val isIncoming: Boolean,
    val memo: String? = null,
    val blockHeight: Long? = null,
    val confirmations: Int? = null,

    // Solana specific
    val slot: Long? = null,
    val computeUnitsConsumed: Long? = null,

    // Bitcoin specific
    val feePerByte: Double? = null,
    val estimatedSize: Int? = null,

    // EVM specific
    val gasPrice: String? = null,
    val gasUsed: Long? = null,
    val nonce: Int? = null,
    val chainId: String? = null,
    val tokenSymbol: String? = null,
    val tokenDecimals: Int? = null,
    val tokenContract: String? = null
)