package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.core.domain.model.CoinType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Network {
    val name: String
    val displayName: String
    val isTestnet: Boolean
    val apiValue: String
    val coinType: CoinType
}

@Serializable
@SerialName("solana")
sealed class SolanaNetwork : Network {
    abstract override val name: String
    abstract override val displayName: String
    abstract override val isTestnet: Boolean
    override val coinType: CoinType = CoinType.SOLANA
    override val apiValue: String = when (this) {
        Mainnet -> "mainnet"
        Devnet -> "devnet"
    }

    @Serializable
    @SerialName("SolanaMainnet")
    data object Mainnet : SolanaNetwork() {
        override val name = "SolanaMainnet"
        override val displayName = "Mainnet"
        override val isTestnet = false
    }

    @Serializable
    @SerialName("SolanaDevnet")
    data object Devnet : SolanaNetwork() {
        override val name = "SolanaDevnet"
        override val displayName = "Devnet"
        override val isTestnet = true
    }
}

@Serializable
@SerialName("bitcoin")
sealed class BitcoinNetwork : Network {
    abstract override val name: String
    abstract override val displayName: String
    abstract override val isTestnet: Boolean
    override val coinType: CoinType = CoinType.BITCOIN
    override val apiValue: String = if (isTestnet) "testnet" else "mainnet"

    @Serializable
    @SerialName("BitcoinMainnet")
    data object Mainnet : BitcoinNetwork() {
        override val name = "BitcoinMainnet"
        override val displayName = "Bitcoin"
        override val isTestnet = false
    }

    @Serializable
    @SerialName("BitcoinTestnet")
    data object Testnet : BitcoinNetwork() {
        override val name = "BitcoinTestnet"
        override val displayName = "Bitcoin Testnet"
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
    abstract override val displayName: String
    abstract override val isTestnet: Boolean
    override val coinType: CoinType = CoinType.ETHEREUM
    override val apiValue: String = when (this) {
        Mainnet -> "mainnet"
        Sepolia -> "sepolia"
    }

    @Serializable
    @SerialName("Mainnet")
    data object Mainnet : EthereumNetwork() {
        override val chainId = "1"
        override val usdcContractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        override val usdtContractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        override val isTestnet = false
        override val displayName = "Ethereum Mainnet"
        override val name = "EthereumMainnet"
    }

    @Serializable
    @SerialName("Sepolia")
    data object Sepolia : EthereumNetwork() {
        override val chainId = "11155111"
        override val usdcContractAddress = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
        override val usdtContractAddress = "0x7169D38820dfd117C3FA1f22a697dBA58d90BA06"
        override val isTestnet = true
        override val displayName = "Ethereum Sepolia"
        override val name = "EthereumSepolia"
    }
}