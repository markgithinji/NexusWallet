package com.example.nexuswallet.feature.coin.ethereum.domain.model

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable

@Serializable
sealed class EVMTransaction {
    abstract val id: String
    abstract val walletId: String
    abstract val fromAddress: String
    abstract val toAddress: String
    abstract val status: TransactionStatus
    abstract val timestamp: Long
    abstract val note: String?
    abstract val feeLevel: FeeLevel
    abstract val gasPriceWei: String
    abstract val gasPriceGwei: String
    abstract val gasLimit: Long
    abstract val feeWei: String
    abstract val feeEth: String
    abstract val nonce: Int
    abstract val chainId: Long
    abstract val signedHex: String?
    abstract val txHash: String?
    abstract val network: String
    abstract val isIncoming: Boolean
    abstract val tokenExternalId: String?
}