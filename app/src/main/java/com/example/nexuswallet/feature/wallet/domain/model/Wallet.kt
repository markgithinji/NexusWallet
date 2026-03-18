package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
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
sealed interface Coin {
    val address: String
    val publicKey: String
    val network: Network
    val symbol: String
    val name: String
}

@Serializable
sealed class EVMToken : Coin {
    abstract override val address: String
    abstract override val publicKey: String
    abstract override val network: EthereumNetwork
    abstract override val symbol: String
    abstract override val name: String
    abstract val contractAddress: String
    abstract val decimals: Int
    abstract val evmTokenType: EVMTokenType
}

@Serializable
data class NativeETH(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String = "0x0000000000000000000000000000000000000000",
    override val symbol: String = "ETH",
    override val name: String = "Ethereum",
    override val decimals: Int = 18,
    override val evmTokenType: EVMTokenType = EVMTokenType.NATIVE
) : EVMToken()

@Serializable
data class USDCToken(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String = network.usdcContractAddress,
    override val symbol: String = "USDC",
    override val name: String = "USD Coin",
    override val decimals: Int = 6,
    override val evmTokenType: EVMTokenType = EVMTokenType.USDC
) : EVMToken()

@Serializable
data class USDTToken(
    override val address: String,
    override val publicKey: String,
    override val network: EthereumNetwork,
    override val contractAddress: String,
    override val symbol: String = "USDT",
    override val name: String = "Tether USD",
    override val decimals: Int = 6,
    override val evmTokenType: EVMTokenType = EVMTokenType.USDT
) : EVMToken()

@Serializable
data class BitcoinCoin(
    override val address: String,
    override val publicKey: String,
    override val symbol: String = "BTC",
    override val name: String = "Bitcoin",
    val derivationPath: String = "m/44'/0'/0'/0/0",
    override val network: BitcoinNetwork,
    val xpub: String
) : Coin

@Serializable
data class SolanaCoin(
    override val address: String,
    override val publicKey: String,
    override val symbol: String = "SOL",
    override val name: String = "Solana",
    val derivationPath: String = "m/44'/501'/0'/0'",
    override val network: SolanaNetwork,
    val splTokens: List<SPLToken> = emptyList()
) : Coin

@Serializable
data class SPLToken(
    val mintAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int
)