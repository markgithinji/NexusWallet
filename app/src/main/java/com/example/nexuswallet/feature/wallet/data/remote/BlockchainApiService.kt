package com.example.nexuswallet.feature.wallet.data.remote
import com.example.nexuswallet.feature.wallet.data.repository.BlockstreamTransaction
import com.example.nexuswallet.feature.wallet.data.repository.BlockstreamUtxo
import com.example.nexuswallet.feature.wallet.data.repository.CovalentBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanTransactionsResponse
import com.example.nexuswallet.feature.wallet.data.repository.GasPriceResponse
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface EtherscanApiService {
    @GET("v2/api")
    suspend fun getEthereumBalance(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "balance",
        @Query("address") address: String,
        @Query("tag") tag: String = "latest",
        @Query("apikey") apiKey: String
    ): EtherscanBalanceResponse

    @GET("v2/api")
    suspend fun getEthereumTransactions(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("address") address: String,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String
    ): EtherscanTransactionsResponse

    @GET("v2/api")
    suspend fun getGasPrice(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "gastracker",
        @Query("action") action: String = "gasoracle",
        @Query("apikey") apiKey: String
    ): GasPriceResponse

    @GET("v2/api")
    suspend fun getTransactionCount(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "proxy",
        @Query("action") action: String = "eth_getTransactionCount",
        @Query("address") address: String,
        @Query("tag") tag: String = "latest",
        @Query("apikey") apiKey: String
    ): EtherscanTransactionCountResponse

    @GET("v2/api")
    suspend fun broadcastTransaction(
        @Query("chainid") chainId: String,
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

interface BlockstreamApiService {
    @GET("address/{address}/utxo")
    suspend fun getBitcoinUtxos(@Path("address") address: String): List<BlockstreamUtxo>

    @GET("address/{address}/transactions")
    suspend fun getBitcoinTransactions(@Path("address") address: String): List<BlockstreamTransaction>
}

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

interface BitcoinBroadcastApiService {
    /**
     * Mempool.space API - Free, no API key needed
     * Broadcasts raw Bitcoin transaction
     */
    @POST("api/tx")
    @Headers("Content-Type: text/plain")
    suspend fun broadcastBitcoinTransaction(
        @Body rawTx: String
    ): Response<String>

    /**
     * Alternative: Get recommended fees
     */
    @GET("api/v1/fees/recommended")
    suspend fun getBitcoinFees(): BitcoinFeesResponse
}

@Serializable
data class BitcoinFeesResponse(
    @SerialName("fastestFee") val fastestFee: Int,
    @SerialName("halfHourFee") val halfHourFee: Int,
    @SerialName("hourFee") val hourFee: Int,
    @SerialName("economyFee") val economyFee: Int,
    @SerialName("minimumFee") val minimumFee: Int
)

enum class ChainId(val id: Int) {
    ETHEREUM_MAINNET(1),
    POLYGON(137),
    BINANCE_SMART_CHAIN(56),
    ARBITRUM(42161),
    OPTIMISM(10)
}