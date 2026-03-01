package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.wallet.data.local.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.SPLTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.WalletEntity
import java.util.UUID

// ===== Wallet Mappers =====
fun WalletEntity.toDomain(
    bitcoinCoins: List<BitcoinCoin>,
    solanaCoins: List<SolanaCoin>,
    evmTokens: List<EVMToken>
): Wallet = Wallet(
    id = id,
    name = name,
    mnemonicHash = mnemonicHash,
    createdAt = createdAt,
    isBackedUp = isBackedUp,
    bitcoinCoins = bitcoinCoins,
    solanaCoins = solanaCoins,
    evmTokens = evmTokens
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
    network = network.toBitcoinNetwork(),
    xpub = xpub
)

fun BitcoinCoin.toEntity(walletId: String): BitcoinCoinEntity = BitcoinCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network.toStorageString(),
    xpub = xpub
)

// ===== Solana Coin Mappers =====
fun SolanaCoinEntity.toDomain(splTokens: List<SPLToken>): SolanaCoin = SolanaCoin(
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network.toSolanaNetwork(),
    splTokens = splTokens
)

fun SolanaCoin.toEntity(walletId: String): SolanaCoinEntity = SolanaCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network.toStorageString()
)

// ===== SPL Token Mappers =====
fun SPLTokenEntity.toDomain(): SPLToken = SPLToken(
    mintAddress = mintAddress,
    symbol = symbol,
    name = name,
    decimals = decimals
)

fun SPLToken.toEntity(solanaCoinId: String): SPLTokenEntity = SPLTokenEntity(
    id = UUID.randomUUID().toString(),
    solanaCoinId = solanaCoinId,
    mintAddress = mintAddress,
    symbol = symbol,
    name = name,
    decimals = decimals
)

// ===== EVM Token Mappers =====
fun EVMToken.toEntity(walletId: String): EVMTokenEntity = EVMTokenEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = "m/44'/60'/0'/0/0",
    network = network.toStorageString(),
    contractAddress = contractAddress,
    symbol = symbol,
    name = name,
    decimals = decimals,
    tokenType = when (this) {
        is NativeETH -> "NATIVE"
        is USDCToken -> "USDC"
        is USDTToken -> "USDT"
        is ERC20Token -> "ERC20"
    },
    externalId = externalId,
    updatedAt = System.currentTimeMillis()
)

fun EVMTokenEntity.toDomain(): EVMToken {
    val network = network.toEthereumNetwork()

    return when (tokenType) {
        "NATIVE" -> NativeETH(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress
        )
        "USDC" -> USDCToken(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress
        )
        "USDT" -> USDTToken(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress
        )
        else -> ERC20Token(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress,
            symbol = symbol,
            name = name,
            decimals = decimals
        )
    }
}

// ===== Bitcoin Balance Mappers =====
fun BitcoinBalanceEntity.toDomain(): BitcoinBalance = BitcoinBalance(
    address = address,
    satoshis = satoshis,
    btc = btc,
    usdValue = usdValue
)

fun BitcoinBalance.toEntity(coinId: String): BitcoinBalanceEntity = BitcoinBalanceEntity(
    id = UUID.randomUUID().toString(),
    coinId = coinId,
    address = address,
    satoshis = satoshis,
    btc = btc,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)

// ===== Solana Balance Mappers =====
fun SolanaBalanceEntity.toDomain(): SolanaBalance = SolanaBalance(
    address = address,
    lamports = lamports,
    sol = sol,
    usdValue = usdValue
)

fun SolanaBalance.toEntity(coinId: String): SolanaBalanceEntity = SolanaBalanceEntity(
    id = UUID.randomUUID().toString(),
    coinId = coinId,
    address = address,
    lamports = lamports,
    sol = sol,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)
// ===== EVM Balance Mappers =====

fun EVMBalance.toEntity(walletId: String, tokenEntity: EVMTokenEntity): EVMBalanceEntity = EVMBalanceEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    tokenId = tokenEntity.id,
    externalTokenId = externalTokenId,
    address = address,
    balanceWei = balanceWei,
    balanceDecimal = balanceDecimal,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)

fun EVMBalanceEntity.toDomain(): EVMBalance = EVMBalance(
    externalTokenId = externalTokenId,
    address = address,
    balanceWei = balanceWei,
    balanceDecimal = balanceDecimal,
    usdValue = usdValue
)

// ===== Network Conversion Helpers =====

// BitcoinNetwork conversion
fun BitcoinNetwork.toStorageString(): String = when (this) {
    BitcoinNetwork.Mainnet -> "Mainnet"
    BitcoinNetwork.Testnet -> "Testnet"
}

fun String.toBitcoinNetwork(): BitcoinNetwork = when (this) {
    "Mainnet" -> BitcoinNetwork.Mainnet
    "Testnet" -> BitcoinNetwork.Testnet
    else -> BitcoinNetwork.Testnet
}

// SolanaNetwork conversion
fun SolanaNetwork.toStorageString(): String = when (this) {
    SolanaNetwork.Mainnet -> "Mainnet"
    SolanaNetwork.Devnet -> "Devnet"
}

fun String.toSolanaNetwork(): SolanaNetwork = when (this) {
    "Mainnet" -> SolanaNetwork.Mainnet
    "Devnet" -> SolanaNetwork.Devnet
    else -> SolanaNetwork.Devnet
}

// EthereumNetwork conversion
fun EthereumNetwork.toStorageString(): String = when (this) {
    EthereumNetwork.Mainnet -> "Mainnet"
    EthereumNetwork.Sepolia -> "Sepolia"
}

fun String.toEthereumNetwork(): EthereumNetwork = when (this) {
    "Mainnet" -> EthereumNetwork.Mainnet
    "Sepolia" -> EthereumNetwork.Sepolia
    else -> EthereumNetwork.Sepolia
}