package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class Web3jFactory @Inject constructor() {

    fun create(network: EthereumNetwork): Web3j {
        val alchemyApiKey = BuildConfig.ALCHEMY_API_KEY

        val rpcUrl = when (network) {
            EthereumNetwork.Mainnet -> "https://eth-mainnet.g.alchemy.com/v2/$alchemyApiKey"
            EthereumNetwork.Sepolia -> "https://eth-sepolia.g.alchemy.com/v2/$alchemyApiKey"
        }

        return Web3j.build(
            HttpService(
                rpcUrl,
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
        )
    }
}