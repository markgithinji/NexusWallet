package com.example.nexuswallet.feature.ethereum.domain.model

import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import kotlinx.serialization.Serializable

@Serializable
data class EthereumWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: EthereumNetwork
)