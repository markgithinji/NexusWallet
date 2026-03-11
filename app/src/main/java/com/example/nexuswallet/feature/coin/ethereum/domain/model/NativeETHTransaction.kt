package com.example.nexuswallet.feature.coin.ethereum.domain.model

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable

@Serializable
data class NativeETHTransaction(
    override val id: String,
    override val walletId: String,
    override val fromAddress: String,
    override val toAddress: String,
    override val status: TransactionStatus,
    override val timestamp: Long,
    override val note: String?,
    override val feeLevel: FeeLevel,
    val amountWei: String,
    val amountEth: String,
    override val gasPriceWei: String,
    override val gasPriceGwei: String,
    override val gasLimit: Long,
    override val feeWei: String,
    override val feeEth: String,
    override val nonce: Int,
    override val chainId: Long,
    override val signedHex: String?,
    override val txHash: String?,
    override val network: String,
    override val isIncoming: Boolean = false,
    val data: String = "",
    override val tokenExternalId: String? = null
) : EVMTransaction()