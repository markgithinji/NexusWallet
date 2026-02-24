package com.example.nexuswallet.feature.coin.ethereum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EtherscanBalanceResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: String
)

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

@Serializable
data class EtherscanTransactionsResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: List<EtherscanTransactionResponse>
)

@Serializable
data class EtherscanTransactionCountResponse(
    @SerialName("jsonrpc") val jsonrpc: String? = null,
    @SerialName("result") val result: String,
    @SerialName("id") val id: Int? = null
)

@Serializable
data class EtherscanBroadcastResponse(
    @SerialName("jsonrpc") val jsonrpc: String? = null,
    @SerialName("result") val result: String? = null,
    @SerialName("id") val id: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("error") val error: BroadcastErrorResponse? = null
)

@Serializable
data class BroadcastErrorResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String
)

@Serializable
data class GasPriceResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: GasPriceResultResponse
)

@Serializable
data class GasPriceResultResponse(
    @SerialName("LastBlock") val lastBlock: String,
    @SerialName("SafeGasPrice") val SafeGasPrice: String,
    @SerialName("ProposeGasPrice") val ProposeGasPrice: String,
    @SerialName("FastGasPrice") val FastGasPrice: String,
    @SerialName("suggestBaseFee") val suggestBaseFee: String? = null,
    @SerialName("gasUsedRatio") val gasUsedRatio: String? = null
)

@Serializable
data class EtherscanGasEstimateResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: String // Estimated time in seconds
)

@Serializable
data class GasPriceProxyResponse(
    @SerialName("jsonrpc") val jsonrpc: String,
    @SerialName("id") val id: Int,
    @SerialName("result") val result: String
)

@Serializable
data class GasPrice(
    val safe: String,
    val propose: String,
    val fast: String,
    val lastBlock: String? = null,
    val baseFee: String? = null
)