package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import kotlinx.serialization.Serializable

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