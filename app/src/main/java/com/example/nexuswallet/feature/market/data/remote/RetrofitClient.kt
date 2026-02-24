package com.example.nexuswallet.feature.market.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

import com.example.nexuswallet.BuildConfig

object RetrofitClient {
    // CoinGecko API
    private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
    private val COINGECKO_API_KEY = BuildConfig.COINGECKO_API_KEY

    // CryptoPanic API
    private const val CRYPTOPANIC_BASE_URL = "https://cryptopanic.com/api/developer/v2/"
    private val CRYPTOPANIC_API_KEY = BuildConfig.CRYPTOPANIC_API_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Common OkHttp client
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // CoinGecko Retrofit instance (with API key interceptor)
    private val coinGeckoRetrofit = Retrofit.Builder()
        .baseUrl(COINGECKO_BASE_URL)
        .client(
            okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val url = original.url.newBuilder()
                        .addQueryParameter("x_cg_demo_api_key", COINGECKO_API_KEY)
                        .build()
                    val request = original.newBuilder()
                        .url(url)
                        .build()
                    chain.proceed(request)
                }
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val cryptoPanicRetrofit = Retrofit.Builder()
        .baseUrl(CRYPTOPANIC_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val coinGeckoApi: CoinGeckoApi by lazy {
        coinGeckoRetrofit.create(CoinGeckoApi::class.java)
    }

    val cryptoPanicApi: CryptoPanicApi by lazy {
        cryptoPanicRetrofit.create(CryptoPanicApi::class.java)
    }
}