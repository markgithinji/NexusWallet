package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.local.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.EthereumBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.EthereumCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.USDCBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.USDCCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.WalletEntity
import java.util.UUID


// ===== Wallet Mappers =====
fun WalletEntity.toDomain(
    bitcoinCoin: BitcoinCoin?,
    ethereumCoin: EthereumCoin?,
    solanaCoin: SolanaCoin?,
    usdcCoin: USDCCoin?
): Wallet = Wallet(
    id = id,
    name = name,
    mnemonicHash = mnemonicHash,
    createdAt = createdAt,
    isBackedUp = isBackedUp,
    bitcoin = bitcoinCoin,
    ethereum = ethereumCoin,
    solana = solanaCoin,
    usdc = usdcCoin
)

fun Wallet.toEntity(): WalletEntity = WalletEntity(
    id = id,
    name = name,
    mnemonicHash = mnemonicHash,
    createdAt = createdAt,
    isBackedUp = isBackedUp
)

// ===== Bitcoin Coin Mappers =====
fun BitcoinCoinEntity.toDomain(): BitcoinCoin = BitcoinCoin(
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network,
    xpub = xpub
)

fun BitcoinCoin.toEntity(walletId: String): BitcoinCoinEntity = BitcoinCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network,
    xpub = xpub
)

// ===== Ethereum Coin Mappers =====
fun EthereumCoinEntity.toDomain(): EthereumCoin = EthereumCoin(
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network
)

fun EthereumCoin.toEntity(walletId: String): EthereumCoinEntity = EthereumCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network
)

// ===== Solana Coin Mappers =====
fun SolanaCoinEntity.toDomain(): SolanaCoin = SolanaCoin(
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath
)

fun SolanaCoin.toEntity(walletId: String): SolanaCoinEntity = SolanaCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath
)

// ===== USDC Coin Mappers =====
fun USDCCoinEntity.toDomain(): USDCCoin = USDCCoin(
    address = address,
    publicKey = publicKey,
    network = network.toEthereumNetwork(),
    contractAddress = contractAddress
)

fun USDCCoin.toEntity(walletId: String): USDCCoinEntity = USDCCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    network = network.toStorageString(),
    contractAddress = contractAddress
)

// ===== Bitcoin Balance Mappers =====
fun BitcoinBalanceEntity.toDomain(): BitcoinBalance = BitcoinBalance(
    address = address,
    satoshis = satoshis,
    btc = btc,
    usdValue = usdValue
)

fun BitcoinBalance.toEntity(walletId: String): BitcoinBalanceEntity = BitcoinBalanceEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    satoshis = satoshis,
    btc = btc,
    usdValue = usdValue
)

// ===== Ethereum Balance Mappers =====
fun EthereumBalanceEntity.toDomain(): EthereumBalance = EthereumBalance(
    address = address,
    wei = wei,
    eth = eth,
    usdValue = usdValue
)

fun EthereumBalance.toEntity(walletId: String): EthereumBalanceEntity = EthereumBalanceEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    wei = wei,
    eth = eth,
    usdValue = usdValue
)

// ===== Solana Balance Mappers =====
fun SolanaBalanceEntity.toDomain(): SolanaBalance = SolanaBalance(
    address = address,
    lamports = lamports,
    sol = sol,
    usdValue = usdValue
)

fun SolanaBalance.toEntity(walletId: String): SolanaBalanceEntity = SolanaBalanceEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    lamports = lamports,
    sol = sol,
    usdValue = usdValue
)

// ===== USDC Balance Mappers =====
fun USDCBalanceEntity.toDomain(): USDCBalance = USDCBalance(
    address = address,
    amount = amount,
    amountDecimal = amountDecimal,
    usdValue = usdValue
)

fun USDCBalance.toEntity(walletId: String): USDCBalanceEntity = USDCBalanceEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    amount = amount,
    amountDecimal = amountDecimal,
    usdValue = usdValue
)

// ===== Combined WalletBalance Mapper =====
fun mapToWalletBalance(
    walletId: String,
    bitcoinBalance: BitcoinBalanceEntity?,
    ethereumBalance: EthereumBalanceEntity?,
    solanaBalance: SolanaBalanceEntity?,
    usdcBalance: USDCBalanceEntity?
): WalletBalance {
    val timestamps = listOfNotNull(
        bitcoinBalance?.updatedAt,
        ethereumBalance?.updatedAt,
        solanaBalance?.updatedAt,
        usdcBalance?.updatedAt
    )

    return WalletBalance(
        walletId = walletId,
        lastUpdated = timestamps.maxOrNull() ?: System.currentTimeMillis(),
        bitcoin = bitcoinBalance?.toDomain(),
        ethereum = ethereumBalance?.toDomain(),
        solana = solanaBalance?.toDomain(),
        usdc = usdcBalance?.toDomain()
    )
}

// ===== Helper Extensions for EthereumNetwork in USDCCoin =====
/**
 * Convert EthereumNetwork to a simple string for storage in USDC coin table
 * Since EthereumNetwork uses data objects, we can use the simple name
 */
fun EthereumNetwork.toStorageString(): String = when (this) {
    EthereumNetwork.Mainnet -> "Mainnet"
    EthereumNetwork.Sepolia -> "Sepolia"
}

/**
 * Convert stored string back to EthereumNetwork for USDC coin
 */
fun String.toEthereumNetwork(): EthereumNetwork = when (this) {
    "Mainnet" -> EthereumNetwork.Mainnet
    "Sepolia" -> EthereumNetwork.Sepolia
    else -> EthereumNetwork.Sepolia // Default fallback
}