package com.example.nexuswallet.data.remote

import com.example.nexuswallet.data.model.CoinGeckoTokenDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true
    ): List<CoinGeckoTokenDto>
}