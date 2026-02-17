package com.example.nexuswallet.feature.coin.usdc

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.math.BigDecimal
import java.math.RoundingMode
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