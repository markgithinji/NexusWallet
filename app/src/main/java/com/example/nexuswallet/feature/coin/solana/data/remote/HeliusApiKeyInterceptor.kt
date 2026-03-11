package com.example.nexuswallet.feature.coin.solana.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class HeliusApiKeyInterceptor @Inject constructor(
    private val apiKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // Add API key as query parameter
        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("api-key", apiKey)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}