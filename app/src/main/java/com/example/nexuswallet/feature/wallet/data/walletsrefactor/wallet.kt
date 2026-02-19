package com.example.nexuswallet.feature.wallet.data.walletsrefactor
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.solana.SolanaNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.serialization.Serializable

@Serializable
data class Wallet(
    val id: String,
    val name: String,
    val mnemonicHash: String,
    val createdAt: Long,
    val isBackedUp: Boolean = false,
    val bitcoin: BitcoinCoin? = null,
    val ethereum: EthereumCoin? = null,
    val solana: SolanaCoin? = null,
    val usdc: USDCCoin? = null
)

@Serializable
data class BitcoinCoin(
    val address: String,
    val publicKey: String,
    val derivationPath: String = "m/44'/0'/0'/0/0",
    val network: BitcoinNetwork,
    val xpub: String
)

@Serializable
data class EthereumCoin(
    val address: String,
    val publicKey: String,
    val derivationPath: String = "m/44'/60'/0'/0/0",
    val network: EthereumNetwork
)

@Serializable
data class SolanaCoin(
    val address: String,
    val publicKey: String,
    val derivationPath: String = "m/44'/501'/0'/0'",
    val network: SolanaNetwork = SolanaNetwork.DEVNET
)

@Serializable
data class USDCCoin(
    val address: String,
    val publicKey: String,
    val network: EthereumNetwork,
    val contractAddress: String // USDC contract for this network
)