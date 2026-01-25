package com.example.nexuswallet.feature.wallet.domain

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
    POLYGON,
    BINANCE_SMART_CHAIN,
    SOLANA,
    ARBITRUM,
    OPTIMISM
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

enum class WalletType {
    BITCOIN, ETHEREUM, MULTICHAIN, SOLANA
}

enum class BitcoinNetwork {
    MAINNET, TESTNET, REGTEST
}

enum class EthereumNetwork {
    MAINNET, ROPSTEN, GOERLI, SEPOLIA, POLYGON, BSC, ARBITRUM, OPTIMISM
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
data class CreatedWallet(
    val id: String,
    val name: String,
    val type: WalletType,
    val address: String,
    val mnemonicHash: String,
    val createdAt: Long,
    val chains: List<ChainType>
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

// Token model (for ERC20 tokens)
@Serializable
data class ERC20Token(
    val contractAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val logoUrl: String? = null,
    val chain: ChainType,
    val priceUsd: Double? = null,
    val marketCap: Double? = null
)

@Serializable
data class WalletImportData(
    val mnemonic: List<String>? = null,
    val privateKey: String? = null,
    val keystoreJson: String? = null,
    val password: String? = null,
    val walletType: WalletType
)

@Serializable
data class WalletSettings(
    val walletId: String,
    val requireBiometricForTransactions: Boolean = true,
    val autoLockTimeout: Int = 300, // seconds
    val currency: String = "USD",
    val hideSmallBalances: Boolean = false,
    val rpcUrls: Map<ChainType, String> = emptyMap()
)

@Serializable
data class NetworkFee(
    val chain: ChainType,
    val baseFee: String? = null,
    val priorityFee: String? = null,
    val gasPrice: String? = null,
    val estimatedTime: Int, // seconds
    val feeLevel: FeeLevel
)

enum class FeeLevel {
    SLOW, NORMAL, FAST, CUSTOM
}

// For QR code generation
@Serializable
data class AddressQRCode(
    val address: String,
    val amount: String? = null,
    val chain: ChainType,
    val memo: String? = null,
    val qrCodeData: String
)

@Serializable
data class WalletConnectSession(
    val sessionId: String,
    val peerMeta: WalletConnectPeerMeta,
    val chainId: Int,
    val accounts: List<String>,
    val connectedAt: Long
)

@Serializable
data class WalletConnectPeerMeta(
    val name: String,
    val url: String,
    val description: String? = null,
    val icons: List<String> = emptyList()
)