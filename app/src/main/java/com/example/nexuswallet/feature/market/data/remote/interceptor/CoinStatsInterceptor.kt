package com.example.nexuswallet.feature.market.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import com.example.nexuswallet.BuildConfig

class CoinStatsInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Add X-API-KEY header
        val newRequest = originalRequest.newBuilder()
            .addHeader("X-API-KEY", BuildConfig.COINSTATS_API_KEY)
            .addHeader("accept", "application/json")
            .build()

        return chain.proceed(newRequest)
    }
}
