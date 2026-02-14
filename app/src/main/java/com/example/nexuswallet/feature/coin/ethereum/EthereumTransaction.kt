package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.BigInteger

data class EthereumTransaction(
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountWei: BigInteger,
    val amountEth: BigDecimal,
    val gasPriceWei: BigInteger,
    val gasPriceGwei: BigDecimal,
    val gasLimit: Long,
    val feeWei: BigInteger,
    val feeEth: BigDecimal,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String,
    val data: String
)