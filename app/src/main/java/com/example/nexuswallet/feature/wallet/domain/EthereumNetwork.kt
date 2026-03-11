package com.example.nexuswallet.feature.wallet.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class EthereumNetwork {
    abstract val chainId: String
    abstract val usdcContractAddress: String
    abstract val isTestnet: Boolean
    abstract val displayName: String

    @Serializable
    @SerialName("Mainnet")
    data object Mainnet : EthereumNetwork() {
        override val chainId = "1"
        override val usdcContractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        override val isTestnet = false
        override val displayName = "Ethereum Mainnet"
    }

    @Serializable
    @SerialName("Sepolia")
    data object Sepolia : EthereumNetwork() {
        override val chainId = "11155111"
        override val usdcContractAddress = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
        override val isTestnet = true
        override val displayName = "Ethereum Sepolia"
    }
}