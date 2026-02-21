package com.example.nexuswallet.feature.coin.ethereum

import retrofit2.http.GET
import retrofit2.http.Query

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
        @Query("startblock") startblock: Int = 0,
        @Query("endblock") endblock: Int = 999999999,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 100,  // Get up to 100 transactions
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
        @Query("tag") tag: String = "pending",
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

    @GET("v2/api")
    suspend fun getTokenTransfers(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "tokentx",
        @Query("address") address: String,
        @Query("contractaddress") contractAddress: String,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String
    ): EtherscanTokenTransfersResponse

    @GET("v2/api")
    suspend fun getConfirmationTimeEstimate(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "gastracker",
        @Query("action") action: String = "gasestimate",
        @Query("gasprice") gasPriceWei: String, // Gas price in wei
        @Query("apikey") apiKey: String
    ): EtherscanGasEstimateResponse

    @GET("v2/api")
    suspend fun getGasPriceProxy(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "proxy",
        @Query("action") action: String = "eth_gasPrice",
        @Query("apikey") apiKey: String
    ): GasPriceProxyResponse
}