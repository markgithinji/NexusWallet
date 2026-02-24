package com.example.nexuswallet.feature.market.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import com.example.nexuswallet.feature.market.data.model.CryptoPanicResponse

interface CryptoPanicApi {
    @GET("posts/")
    suspend fun getNews(
        @Query("auth_token") authToken: String,
        @Query("public") public: Boolean = true,
        @Query("currencies") currencies: String? = null,  // Filter by coin
        @Query("filter") filter: String? = null,          // Optional: rising, hot, etc.
        @Query("kind") kind: String = "news",
        @Query("regions") regions: String = "en"
    ): CryptoPanicResponse
}