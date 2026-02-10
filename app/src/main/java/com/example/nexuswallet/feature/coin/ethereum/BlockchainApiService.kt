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

    @GET("v2/api")
    suspend fun getTransactionReceiptStatus(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "transaction",
        @Query("action") action: String = "gettxreceiptstatus",
        @Query("txhash") txhash: String,
        @Query("apikey") apiKey: String
    ): TransactionReceiptStatusResponse

    @GET("v2/api")
    suspend fun getTokenBalance(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "tokenbalance",
        @Query("contractaddress") contractAddress: String,
        @Query("address") address: String,
        @Query("tag") tag: String = "latest",
        @Query("apikey") apiKey: String
    ): EtherscanBalanceResponse

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
    suspend fun getContractABI(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "contract",
        @Query("action") action: String = "getabi",
        @Query("address") address: String,
        @Query("apikey") apiKey: String
    ): EtherscanContractABIResponse

    @GET("v2/api")
    suspend fun getTokenTotalSupply(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "stats",
        @Query("action") action: String = "tokensupply",
        @Query("contractaddress") contractAddress: String,
        @Query("apikey") apiKey: String
    ): EtherscanTokenSupplyResponse

    @GET("v2/api")
    suspend fun getGasOracle(
        @Query("chainid") chainId: String,
        @Query("module") module: String = "gastracker",
        @Query("action") action: String = "gasoracle",
        @Query("apikey") apiKey: String
    ): GasPriceResponse
}