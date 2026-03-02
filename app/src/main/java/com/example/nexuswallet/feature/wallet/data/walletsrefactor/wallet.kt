package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Chain {
    @Serializable
    @SerialName("Bitcoin")
    data object Bitcoin : Chain()

    @Serializable
    @SerialName("Ethereum")
    data object Ethereum : Chain()

    @Serializable
    @SerialName("Solana")
    data object Solana : Chain()
}

@Serializable
sealed class BitcoinNetwork {
    abstract val name: String
    abstract val displayName: String
    abstract val isTestnet: Boolean

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
sealed class SolanaNetwork {
    abstract val name: String

    @Serializable
    @SerialName("SolanaMainnet")
    data object Mainnet : SolanaNetwork() {
        override val name = "SolanaMainnet"
    }

    @Serializable
    @SerialName("SolanaDevnet")
    data object Devnet : SolanaNetwork() {
        override val name = "SolanaDevnet"
    }
}
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

@Serializable
sealed class EVMToken {
    abstract val address: String
    abstract val publicKey: String
    abstract val network: EthereumNetwork
    abstract val contractAddress: String
    abstract val symbol: String
    abstract val name: String
    abstract val decimals: Int
    abstract val externalId: String
}

@Serializable
data class NativeETH(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String = "0x0000000000000000000000000000000000000000",
    override val symbol: String = "ETH",
    override val name: String = "Ethereum",
    override val decimals: Int = 18
) : EVMToken() {
    override val externalId: String = "${network.chainId}_eth"
}

@Serializable
data class ERC20Token(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String,
    override val symbol: String,
    override val name: String,
    override val decimals: Int
) : EVMToken() {
    override val externalId: String = "${network.chainId}_${contractAddress}"
}

@Serializable
data class USDCToken(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String = network.usdcContractAddress,
    override val symbol: String = "USDC",
    override val name: String = "USD Coin",
    override val decimals: Int = 6
) : EVMToken() {
    override val externalId: String = "${network.chainId}_usdc"
}

@Serializable
data class USDTToken(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String,
    override val symbol: String = "USDT",
    override val name: String = "Tether USD",
    override val decimals: Int = 6
) : EVMToken() {
    override val externalId: String = "${network.chainId}_usdt"
}

@Serializable
data class BitcoinCoin(
    val address: String,
    val publicKey: String,
    val derivationPath: String = "m/44'/0'/0'/0/0",
    val network: BitcoinNetwork,
    val xpub: String
)

@Serializable
data class SolanaCoin(
    val address: String,
    val publicKey: String,
    val derivationPath: String = "m/44'/501'/0'/0'",
    val network: SolanaNetwork,
    val splTokens: List<SPLToken> = emptyList()
)

@Serializable
data class SPLToken(
    val mintAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int
)

@Serializable
data class Wallet(
    val id: String,
    val name: String,
    val mnemonicHash: String,
    val createdAt: Long,
    val isBackedUp: Boolean = false,
    val bitcoinCoins: List<BitcoinCoin> = emptyList(),
    val solanaCoins: List<SolanaCoin> = emptyList(),
    val evmTokens: List<EVMToken> = emptyList()
)