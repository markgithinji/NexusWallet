package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import kotlinx.serialization.Serializable

@Serializable
data class TokenBalance(
    val tokenId: String,
    val symbol: String,
    val name: String,
    val contractAddress: String,
    val balance: String,
    val balanceDecimal: String,
    val usdPrice: Double,
    val usdValue: Double,
    val logoUrl: String? = null,
    val decimals: Int = 18,
    val chain: ChainType
)

enum class ChainType {
    BITCOIN,
    ETHEREUM,
    ETHEREUM_SEPOLIA,
    SOLANA
}

@Serializable
sealed class CryptoWallet {
    abstract val id: String
    abstract val name: String
    abstract val address: String
    abstract val mnemonicHash: String
    abstract val createdAt: Long
    abstract val isBackedUp: Boolean
    abstract val walletType: WalletType
}

@Serializable
data class BitcoinWallet(
    override val id: String,
    override val name: String,
    override val address: String,
    val publicKey: String,
    val privateKeyEncrypted: String,
    val network: BitcoinNetwork,
    val derivationPath: String,
    val xpub: String,
    override val mnemonicHash: String,
    override val createdAt: Long,
    override val isBackedUp: Boolean = false,
    override val walletType: WalletType = WalletType.BITCOIN
) : CryptoWallet()

@Serializable
data class EthereumWallet(
    override val id: String,
    override val name: String,
    override val address: String,
    val publicKey: String,
    val privateKeyEncrypted: String,
    val network: EthereumNetwork,
    val derivationPath: String,
    val isSmartContractWallet: Boolean = false,
    val walletFile: String? = null,
    override val mnemonicHash: String,
    override val createdAt: Long,
    override val isBackedUp: Boolean = false,
    override val walletType: WalletType = WalletType.ETHEREUM
) : CryptoWallet()

@Serializable
data class MultiChainWallet(
    override val id: String,
    override val name: String,
    val bitcoinWallet: BitcoinWallet? = null,
    val ethereumWallet: EthereumWallet? = null,
    val polygonWallet: EthereumWallet? = null,
    val bscWallet: EthereumWallet? = null,
    val solanaWallet: SolanaWallet? = null,
    override val mnemonicHash: String,
    override val createdAt: Long,
    override val isBackedUp: Boolean = false,
    override val walletType: WalletType = WalletType.MULTICHAIN, override val address: String
) : CryptoWallet()

@Serializable
data class SolanaWallet(
    override val id: String,
    override val name: String,
    override val address: String,
    val publicKey: String,
    val privateKeyEncrypted: String,
    override val mnemonicHash: String,
    override val createdAt: Long,
    override val isBackedUp: Boolean = false,
    override val walletType: WalletType = WalletType.SOLANA
) : CryptoWallet()

@Serializable
data class USDCWallet(
    override val id: String,
    override val name: String,
    override val address: String,
    val publicKey: String,
    val privateKeyEncrypted: String,
    val network: EthereumNetwork, // USDC exists on Ethereum, Polygon, etc.
    val contractAddress: String, // USDC contract address for this network
    val parentEthereumWalletId: String? = null, // Optional: link to ETH wallet for gas
    override val mnemonicHash: String,
    override val createdAt: Long,
    override val isBackedUp: Boolean = false,
    override val walletType: WalletType = WalletType.USDC
) : CryptoWallet()

enum class WalletType {
    BITCOIN, ETHEREUM, MULTICHAIN, SOLANA, ETHEREUM_SEPOLIA, USDC
}

enum class EthereumNetwork {
    MAINNET, GOERLI, SEPOLIA, POLYGON, BSC, ARBITRUM, OPTIMISM
}

@Serializable
data class WalletBalance(
    val walletId: String,
    val address: String,
    val nativeBalance: String,
    val nativeBalanceDecimal: String,
    val usdValue: Double,
    val tokens: List<TokenBalance> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class WalletBackup(
    val walletId: String,
    val encryptedMnemonic: String,
    val encryptedPrivateKey: String,
    val encryptionIV: String,
    val backupDate: Long,
    val walletType: WalletType,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class Transaction(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val valueDecimal: String,
    val gasPrice: String? = null,
    val gasUsed: String? = null,
    val timestamp: Long,
    val status: TransactionStatus,
    val chain: ChainType,
    val tokenTransfers: List<TokenTransfer> = emptyList()
)

@Serializable
data class TokenTransfer(
    val token: TokenBalance,
    val from: String,
    val to: String,
    val value: String,
    val valueDecimal: String
)

enum class TransactionStatus {
    PENDING, SUCCESS, FAILED
}