package com.example.nexuswallet.feature.wallet.data.remote

import com.example.nexuswallet.feature.wallet.data.repository.BlockstreamTransaction
import com.example.nexuswallet.feature.wallet.data.repository.BlockstreamUtxo
import com.example.nexuswallet.feature.wallet.data.repository.CovalentBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanTransactionsResponse
import com.example.nexuswallet.feature.wallet.data.repository.GasPriceResponse
import kotlinx.serialization.SerialName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable

interface EtherscanApiService {
    @GET("api")
    suspend fun getEthereumBalance(
        @Query("module") module: String = "account",
        @Query("action") action: String = "balance",
        @Query("address") address: String,
        @Query("tag") tag: String = "latest",
        @Query("apikey") apiKey: String
    ): EtherscanBalanceResponse

    @GET("api")
    suspend fun getEthereumTransactions(
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("address") address: String,
        @Query("startblock") startBlock: Int = 0,
        @Query("endblock") endBlock: Int = 99999999,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 50,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String
    ): EtherscanTransactionsResponse

    @GET("api")
    suspend fun getGasPrice(
        @Query("module") module: String = "gastracker",
        @Query("action") action: String = "gasoracle",
        @Query("apikey") apiKey: String
    ): GasPriceResponse

    @GET("api")
    suspend fun getTransactionCount(
        @Query("module") module: String = "proxy",
        @Query("action") action: String = "eth_getTransactionCount",
        @Query("address") address: String,
        @Query("tag") tag: String = "latest",
        @Query("apikey") apiKey: String
    ): EtherscanTransactionCountResponse

    @GET("api")
    suspend fun broadcastTransaction(
        @Query("module") module: String = "proxy",
        @Query("action") action: String = "eth_sendRawTransaction",
        @Query("hex") hex: String,
        @Query("apikey") apiKey: String
    ): EtherscanBroadcastResponse
}

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
data class EtherscanBalanceResponse(
    @SerialName("status") val status: String,
    @SerialName("result") val result: String
)

@Serializable
data class EtherscanTransactionsResponse(
    @SerialName("status") val status: String,
    @SerialName("result") val result: List<EtherscanTransaction>
)

@Serializable
data class EtherscanTransaction(
    @SerialName("hash") val hash: String,
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    @SerialName("value") val value: String,
    @SerialName("gasPrice") val gasPrice: String,
    @SerialName("gas") val gas: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("isError") val isError: String,
    @SerialName("receiptStatus") val receiptStatus: String
)

@Serializable
data class GasPriceResponse(
    @SerialName("status") val status: String,
    @SerialName("result") val result: GasPriceResult
)

@Serializable
data class GasPriceResult(
    @SerialName("SafeGasPrice") val SafeGasPrice: String,
    @SerialName("ProposeGasPrice") val ProposeGasPrice: String,
    @SerialName("FastGasPrice") val FastGasPrice: String
)

interface BlockstreamApiService {
    @GET("address/{address}/utxo")
    suspend fun getBitcoinUtxos(@Path("address") address: String): List<BlockstreamUtxo>

    @GET("address/{address}/transactions")
    suspend fun getBitcoinTransactions(@Path("address") address: String): List<BlockstreamTransaction>
}

@Serializable
data class BlockstreamUtxo(
    @SerialName("txid") val txid: String,
    @SerialName("vout") val vout: Int,
    @SerialName("value") val value: Long,
    @SerialName("scriptpubkey") val scriptpubkey: String? = null,
    @SerialName("scriptpubkey_asm") val scriptpubkeyAsm: String? = null,
    @SerialName("scriptpubkey_type") val scriptpubkeyType: String? = null,
    @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String? = null,
    @SerialName("status") val status: BlockstreamStatus? = null
)

@Serializable
data class BlockstreamStatus(
    @SerialName("confirmed") val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int? = null,
    @SerialName("block_hash") val blockHash: String? = null,
    @SerialName("block_time") val blockTime: Int? = null
)

@Serializable
data class BlockstreamTransaction(
    @SerialName("txid") val txid: String,
    @SerialName("version") val version: Int,
    @SerialName("locktime") val locktime: Int,
    @SerialName("vin") val vin: List<BlockstreamVin>,
    @SerialName("vout") val vout: List<BlockstreamVout>,
    @SerialName("size") val size: Int,
    @SerialName("weight") val weight: Int,
    @SerialName("fee") val fee: Long,
    @SerialName("status") val status: BlockstreamStatus
)

@Serializable
data class BlockstreamVin(
    @SerialName("txid") val txid: String,
    @SerialName("vout") val vout: Int,
    @SerialName("prevout") val prevout: BlockstreamVout? = null,
    @SerialName("scriptsig") val scriptsig: String? = null,
    @SerialName("scriptsig_asm") val scriptsigAsm: String? = null,
    @SerialName("is_coinbase") val isCoinbase: Boolean,
    @SerialName("sequence") val sequence: Long
)

@kotlinx.serialization.Serializable
data class BlockstreamVout(
    @SerialName("scriptpubkey") val scriptpubkey: String? = null,
    @SerialName("scriptpubkey_asm") val scriptpubkeyAsm: String? = null,
    @SerialName("scriptpubkey_type") val scriptpubkeyType: String? = null,
    @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String? = null,
    @SerialName("value") val value: Long
)

interface CovalentApiService {
    @GET("v1/{chainId}/address/{address}/balances_v2/")
    suspend fun getTokenBalances(
        @Path("chainId") chainId: Int,
        @Path("address") address: String,
        @Query("nft") nft: Boolean = false,
        @Query("no-nft-fetch") noNftFetch: Boolean = true,
        @Query("key") apiKey: String
    ): CovalentBalanceResponse
}

enum class ChainId(val id: Int) {
    ETHEREUM_MAINNET(1),
    POLYGON(137),
    BINANCE_SMART_CHAIN(56),
    ARBITRUM(42161),
    OPTIMISM(10)
}