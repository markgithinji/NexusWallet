package com.example.nexuswallet.feature.solana.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import com.example.nexuswallet.BuildConfig
import javax.inject.Named

class HeliusInterceptor @Inject constructor(
    @Named("helius_api_key") private val apiKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // Add API key as query parameter to all requests
        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("api-key", apiKey)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}