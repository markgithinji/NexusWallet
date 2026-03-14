package com.example.nexuswallet.feature.market.data.remote

import com.example.nexuswallet.feature.market.data.remote.model.cryptopanic.CryptoPanicResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CryptoPanicApi {
    @GET("posts/")
    suspend fun getNews(
        @Query("public") public: Boolean = true,
        @Query("currencies") currencies: String? = null,
        @Query("filter") filter: String? = null,
        @Query("kind") kind: String = "news",
        @Query("regions") regions: String = "en"
    ): CryptoPanicResponse
}