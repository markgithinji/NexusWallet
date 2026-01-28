package com.example.nexuswallet.feature.wallet.data.remote

import com.example.nexuswallet.feature.wallet.data.repository.BlockstreamTransaction
import com.example.nexuswallet.feature.wallet.data.repository.BlockstreamUtxo
import com.example.nexuswallet.feature.wallet.data.repository.CovalentBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanBalanceResponse
import com.example.nexuswallet.feature.wallet.data.repository.EtherscanTransactionsResponse
import com.example.nexuswallet.feature.wallet.data.repository.GasPriceResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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
}

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

enum class ChainId(val id: Int) {
    ETHEREUM_MAINNET(1),
    POLYGON(137),
    BINANCE_SMART_CHAIN(56),
    ARBITRUM(42161),
    OPTIMISM(10)
}