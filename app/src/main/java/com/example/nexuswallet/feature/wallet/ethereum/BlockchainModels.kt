package com.example.nexuswallet.feature.wallet.ethereum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EtherscanBalanceResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: String
)

@Serializable
data class EtherscanTransaction(
    @SerialName("blockNumber") val blockNumber: String,
    @SerialName("timeStamp") val timestamp: String,
    @SerialName("hash") val hash: String,
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    @SerialName("value") val value: String,
    @SerialName("gas") val gas: String,
    @SerialName("gasPrice") val gasPrice: String,
    @SerialName("isError") val isError: String,
    @SerialName("txreceipt_status") val receiptStatus: String
)

@Serializable
data class EtherscanTransactionsResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: List<EtherscanTransaction>
)

@Serializable
data class EtherscanTransactionCountResponse(
    @SerialName("jsonrpc") val jsonrpc: String,
    @SerialName("result") val result: String,
    @SerialName("id") val id: Int
)

@Serializable
data class EtherscanBroadcastResponse(
    @SerialName("jsonrpc") val jsonrpc: String,
    @SerialName("result") val result: String,
    @SerialName("id") val id: Int
)

@Serializable
data class GasPriceResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: GasPriceResult
)

@Serializable
data class GasPriceResult(
    @SerialName("LastBlock") val lastBlock: String,
    @SerialName("SafeGasPrice") val SafeGasPrice: String,
    @SerialName("ProposeGasPrice") val ProposeGasPrice: String,
    @SerialName("FastGasPrice") val FastGasPrice: String,
    @SerialName("suggestBaseFee") val suggestBaseFee: String? = null,
    @SerialName("gasUsedRatio") val gasUsedRatio: String? = null
)

@Serializable
data class GasPrice(
    val safe: String,
    val propose: String,
    val fast: String,
    val lastBlock: String? = null,
    val baseFee: String? = null
)

@Serializable
data class TransactionReceiptStatusResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: TransactionReceiptStatusResult
)

@Serializable
data class TransactionReceiptStatusResult(
    @SerialName("status") val status: String  // "1" = success, "0" = failed
)

@Serializable
data class EtherscanTokenTransfersResponse(
    val status: String,
    val message: String,
    val result: List<TokenTransaction>
)

@Serializable
data class TokenTransaction(
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val from: String,
    val contractAddress: String,
    val to: String,
    val value: String,
    val tokenName: String,
    val tokenSymbol: String,
    val tokenDecimal: String,
    val transactionIndex: String,
    val gas: String,
    val gasPrice: String,
    val gasUsed: String,
    val cumulativeGasUsed: String,
    val input: String,
    val confirmations: String
)

@Serializable
data class EtherscanContractABIResponse(
    val status: String,
    val message: String,
    val result: String
)

@Serializable
data class EtherscanTokenSupplyResponse(
    val status: String,
    val message: String,
    val result: String
)