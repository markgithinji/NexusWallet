package com.example.nexuswallet.feature.ethereum.data.remote

import com.example.nexuswallet.feature.coin.ethereum.data.remote.model.EtherscanBroadcastResponse
import com.example.nexuswallet.feature.coin.ethereum.data.remote.model.EtherscanTransactionsResponse
import com.example.nexuswallet.feature.usdc.domain.EtherscanTokenTransfersResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface EtherscanApiService {
    @GET("v2/api")
    suspend fun getEthereumTransactions(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("address") address: String,
        @Query("startblock") startblock: Int = 0,
        @Query("endblock") endblock: Int = 999999999,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 100, // Get up to 100 transactions
        @Query("sort") sort: String = "desc"
    ): EtherscanTransactionsResponse

    @GET("v2/api")
    suspend fun broadcastTransaction(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "proxy",
        @Query("action") action: String = "eth_sendRawTransaction",
        @Query("hex") hex: String
    ): EtherscanBroadcastResponse

    @GET("v2/api")
    suspend fun getTokenTransfers(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "tokentx",
        @Query("address") address: String,
        @Query("contractaddress") contractAddress: String,
        @Query("sort") sort: String = "desc"
    ): com.example.nexuswallet.feature.usdc.domain.EtherscanTokenTransfersResponse
}