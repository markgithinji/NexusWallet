package com.example.nexuswallet.feature.coin.solana.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HeliusApi {
    @GET("addresses/{address}/transactions")
    suspend fun getTransactions(
        @Path("address") address: String,
        @Query("limit") limit: Int,
    ): List<HeliusTransactionResponse>

    @POST("transactions")
    suspend fun getTransaction(
        @Body request: HeliusTransactionRequest,
    ): List<HeliusTransactionResponse>
}