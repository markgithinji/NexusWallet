package com.example.nexuswallet.feature.wallet.data.remote

import com.example.nexuswallet.feature.wallet.data.repository.EtherscanBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanBroadcastResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanTransactionCountResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanTransactionsResponse
import com.example.nexuswallet.feature.wallet.data.repository.GasPriceResponse
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