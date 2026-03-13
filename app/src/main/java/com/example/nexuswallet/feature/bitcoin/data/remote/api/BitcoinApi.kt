package com.example.nexuswallet.feature.bitcoin.data.remote.api

import com.example.nexuswallet.feature.bitcoin.data.remote.model.AddressResponse
import com.example.nexuswallet.feature.bitcoin.data.remote.model.EsploraTransactionResponse
import com.example.nexuswallet.feature.bitcoin.data.remote.model.UTXOResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface BitcoinApi {
    @GET("address/{address}")
    suspend fun getAddressInfo(@Path("address") address: String): AddressResponse

    @GET("address/{address}/utxo")
    suspend fun getUtxos(@Path("address") address: String): List<UTXOResponse>

    @GET("tx/{txid}")
    suspend fun getTransaction(@Path("txid") txid: String): EsploraTransactionResponse

    @GET("fee-estimates")
    suspend fun getFeeEstimates(): Map<String, Double>

    @POST("tx")
    @Headers("Content-Type: text/plain")
    suspend fun broadcastTransaction(@Body signedHex: String): ResponseBody

    @GET("address/{address}/txs")
    suspend fun getAddressTransactions(@Path("address") address: String): List<EsploraTransactionResponse>
}