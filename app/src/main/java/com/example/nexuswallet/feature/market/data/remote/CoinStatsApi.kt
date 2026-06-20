package com.example.nexuswallet.feature.market.data.remote

import com.example.nexuswallet.feature.market.data.remote.model.coinstats.CoinStatsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinStatsApi {
    @GET("news")
    suspend fun getNews(
        @Query("coinId") coinId: String? = null,
        @Query("type") type: String = "latest",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("source") source: String? = null
    ): CoinStatsResponse
}