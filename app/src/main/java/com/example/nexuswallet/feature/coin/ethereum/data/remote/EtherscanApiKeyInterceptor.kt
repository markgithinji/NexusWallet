package com.example.nexuswallet.feature.coin.ethereum.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class EtherscanApiKeyInterceptor @Inject constructor(
    private val apiKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("apikey", apiKey)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}