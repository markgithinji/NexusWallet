package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.BigInteger

@Serializable
data class EthereumTransaction(
    val id: String,
    val walletId: String,
    val coinType: CoinType = CoinType.ETHEREUM,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountWei: String,
    val amountEth: String,
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String,
    val data: String
)