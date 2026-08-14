package com.example.nexuswallet.feature.wallet.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Network {
    val name: String
    val isTestnet: Boolean
    val nativeSymbol: String
}

@Serializable
@SerialName("solana")
sealed class SolanaNetwork : Network {
    abstract override val name: String
    abstract override val isTestnet: Boolean
    override val nativeSymbol = "SOL"

    @Serializable
    @SerialName("SolanaMainnet")
    data object Mainnet : SolanaNetwork() {
        override val name = "Solana Mainnet"
        override val isTestnet = false
    }

    @Serializable
    @SerialName("SolanaDevnet")
    data object Devnet : SolanaNetwork() {
        override val name = "Solana Devnet"
        override val isTestnet = true
    }
}

@Serializable
@SerialName("bitcoin")
sealed class BitcoinNetwork : Network {
    abstract override val name: String
    abstract override val isTestnet: Boolean
    override val nativeSymbol = "BTC"

    @Serializable
    @SerialName("BitcoinMainnet")
    data object Mainnet : BitcoinNetwork() {
        override val name = "Bitcoin Mainnet"
        override val isTestnet = false
    }

    @Serializable
    @SerialName("BitcoinTestnet")
    data object Testnet : BitcoinNetwork() {
        override val name = "Bitcoin Testnet"
        override val isTestnet = true
    }
}

@Serializable
@SerialName("ethereum")
sealed class EthereumNetwork : Network {
    abstract val chainId: String
    abstract val usdcContractAddress: String
    abstract val usdtContractAddress: String
    abstract override val name: String
    abstract override val isTestnet: Boolean
    override val nativeSymbol = "ETH"

    @Serializable
    @SerialName("Mainnet")
    data object Mainnet : EthereumNetwork() {
        override val chainId = "1"
        override val usdcContractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        override val usdtContractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        override val isTestnet = false
        override val name = "Ethereum Mainnet"
    }

    @Serializable
    @SerialName("Sepolia")
    data object Sepolia : EthereumNetwork() {
        override val chainId = "11155111"
        override val usdcContractAddress = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
        override val usdtContractAddress = "0x7169D38820dfd117C3FA1f22a697dBA58d90BA06"
        override val isTestnet = true
        override val name = "Ethereum Sepolia"
    }
}