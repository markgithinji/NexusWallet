package com.example.nexuswallet.feature.solana.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import com.example.nexuswallet.BuildConfig

class HeliusInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("api-key", BuildConfig.HELIUS_API_KEY)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}