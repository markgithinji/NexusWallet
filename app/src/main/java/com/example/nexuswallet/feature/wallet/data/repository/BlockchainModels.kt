package com.example.nexuswallet.feature.wallet.data.repository

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

// ============ BLOCKSTREAM MODELS ============
@Serializable
data class BlockstreamUtxo(
    @SerialName("txid") val txId: String,
    @SerialName("vout") val vout: Int,
    @SerialName("status") val status: BlockstreamStatus,
    @SerialName("value") val value: Long,
    @SerialName("scriptpubkey") val scriptPubKey: String? = null
)
@Serializable
data class BlockstreamStatus(
    @SerialName("confirmed") val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int?,
    @SerialName("block_time") val blockTime: Long?
)

@Serializable
data class BlockstreamTransaction(
    @SerialName("txid") val txId: String,
    @SerialName("version") val version: Int,
    @SerialName("locktime") val lockTime: Int,
    @SerialName("vin") val inputs: List<BlockstreamInput>,
    @SerialName("vout") val outputs: List<BlockstreamOutput>,
    @SerialName("size") val size: Int,
    @SerialName("weight") val weight: Int,
    @SerialName("fee") val fee: Long,
    @SerialName("status") val status: BlockstreamTransactionStatus
)

@Serializable
data class BlockstreamInput(
    @SerialName("txid") val txId: String,
    @SerialName("vout") val vout: Int,
    @SerialName("prevout") val prevout: BlockstreamOutput?,
    @SerialName("scriptsig") val scriptSig: String?,
    @SerialName("scriptsig_asm") val scriptSigAsm: String?,
    @SerialName("witness") val witness: List<String>?,
    @SerialName("is_coinbase") val isCoinbase: Boolean,
    @SerialName("sequence") val sequence: Long
)

@Serializable
data class BlockstreamOutput(
    @SerialName("scriptpubkey") val scriptPubKey: String,
    @SerialName("scriptpubkey_asm") val scriptPubKeyAsm: String,
    @SerialName("scriptpubkey_type") val scriptPubKeyType: String,
    @SerialName("scriptpubkey_address") val address: String?,
    @SerialName("value") val value: Long
)

@Serializable
data class BlockstreamTransactionStatus(
    @SerialName("confirmed") val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int?,
    @SerialName("block_hash") val blockHash: String?,
    @SerialName("block_time") val blockTime: Long?
)

// ============ COVALENT MODELS ============
@Serializable
data class CovalentTokenBalance(
    @SerialName("contract_address") val contractAddress: String,
    @SerialName("contract_name") val contractName: String,
    @SerialName("contract_ticker_symbol") val symbol: String,
    @SerialName("contract_decimals") val decimals: Int,
    @SerialName("balance") val rawBalance: String,
    @SerialName("quote_rate") val quoteRate: Double?,
    @SerialName("quote") val quote: Double?
)

@Serializable
data class CovalentBalanceResponse(
    @SerialName("data") val data: CovalentData
)

@Serializable
data class CovalentData(
    @SerialName("address") val address: String,
    @SerialName("items") val items: List<CovalentTokenBalance>
)