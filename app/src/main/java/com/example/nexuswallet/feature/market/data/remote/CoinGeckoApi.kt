package com.example.nexuswallet.feature.market.data.remote

import com.example.nexuswallet.feature.market.data.remote.model.coingecko.TokenDetailDto
import com.example.nexuswallet.feature.market.data.remote.model.coingecko.CoinGeckoTokenDto
import com.example.nexuswallet.feature.market.data.remote.model.coingecko.MarketChartDto
import retrofit2.http.GET
import retrofit2.http.Path
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

    @GET("coins/{id}")
    suspend fun getCoinDetails(
        @Path("id") id: String,
        @Query("localization") localization: Boolean = false,
        @Query("tickers") tickers: Boolean = false,
        @Query("market_data") marketData: Boolean = true,
        @Query("community_data") communityData: Boolean = false,
        @Query("developer_data") developerData: Boolean = false,
        @Query("sparkline") sparkline: Boolean = true
    ): TokenDetailDto
    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String, // "1", "7", "30", "90", "365", "max"
        @Query("interval") interval: String? = null // Optional: "daily" for >90 days
    ): MarketChartDto

    @GET("simple/price")
    suspend fun getSimplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") include24hChange: Boolean = false
    ): Map<String, Map<String, Double>>
}