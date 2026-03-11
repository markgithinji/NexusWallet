package com.example.nexuswallet.feature.coin.ethereum.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EtherscanTransactionResponse(
    @SerialName("blockNumber") val blockNumber: String,
    @SerialName("timeStamp") val timestamp: String,
    @SerialName("hash") val hash: String,
    @SerialName("nonce") val nonce: String,
    @SerialName("blockHash") val blockHash: String,
    @SerialName("transactionIndex") val transactionIndex: String,
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    @SerialName("value") val value: String,
    @SerialName("gas") val gas: String,
    @SerialName("gasPrice") val gasPrice: String,
    @SerialName("isError") val isError: String,
    @SerialName("txreceipt_status") val receiptStatus: String,
    @SerialName("input") val input: String,
    @SerialName("contractAddress") val contractAddress: String,
    @SerialName("cumulativeGasUsed") val cumulativeGasUsed: String,
    @SerialName("gasUsed") val gasUsed: String,
    @SerialName("confirmations") val confirmations: String,
    @SerialName("methodId") val methodId: String? = null,
    @SerialName("functionName") val functionName: String? = null
)