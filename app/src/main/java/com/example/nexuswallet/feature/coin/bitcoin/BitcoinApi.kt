package com.example.nexuswallet.feature.coin.bitcoin

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BitcoinApi {
    @GET("address/{address}")
    suspend fun getAddressInfo(@Path("address") address: String): AddressResponse

    @GET("address/{address}/utxo")
    suspend fun getUtxos(@Path("address") address: String): List<UtxoResponse>

    @GET("tx/{txid}")
    suspend fun getTransaction(@Path("txid") txid: String): TransactionResponse

    @GET("fee-estimates")
    suspend fun getFeeEstimates(): Map<String, Double>

    @POST("tx")
    @Headers("Content-Type: text/plain")
    suspend fun broadcastTransaction(@Body signedHex: String): ResponseBody

    @GET("address/{address}/txs")
    suspend fun getAddressTransactions(@Path("address") address: String): List<EsploraTransaction>
}