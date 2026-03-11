package com.example.nexuswallet.feature.wallet.data.local

import com.example.nexuswallet.feature.wallet.data.local.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.SPLTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import java.util.UUID

// ===== Wallet Mappers =====
fun WalletEntity.toDomain(
    bitcoinCoins: List<com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin>,
    solanaCoins: List<com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin>,
    evmTokens: List<com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken>
): com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet(
        id = id,
        name = name,
        mnemonicHash = mnemonicHash,
        createdAt = createdAt,
        isBackedUp = isBackedUp,
        bitcoinCoins = bitcoinCoins,
        solanaCoins = solanaCoins,
        evmTokens = evmTokens
    )

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet.toEntity(): WalletEntity = WalletEntity(
    id = id,
    name = name,
    mnemonicHash = mnemonicHash,
    createdAt = createdAt,
    isBackedUp = isBackedUp
)

// ===== Bitcoin Coin Mappers =====
fun BitcoinCoinEntity.toDomain(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin(
        address = address,
        publicKey = publicKey,
        derivationPath = derivationPath,
        network = network.toBitcoinNetwork(),
        xpub = xpub
    )

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin.toEntity(walletId: String): BitcoinCoinEntity = BitcoinCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network.toStorageString(),
    xpub = xpub
)

// ===== Solana Coin Mappers =====
fun SolanaCoinEntity.toDomain(splTokens: List<com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken>): com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin(
        address = address,
        publicKey = publicKey,
        derivationPath = derivationPath,
        network = network.toSolanaNetwork(),
        splTokens = splTokens
    )

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin.toEntity(walletId: String): SolanaCoinEntity = SolanaCoinEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network.toStorageString()
)

// ===== SPL Token Mappers =====
fun SPLTokenEntity.toDomain(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken(
        mintAddress = mintAddress,
        symbol = symbol,
        name = name,
        decimals = decimals
    )

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken.toEntity(solanaCoinId: String): SPLTokenEntity = SPLTokenEntity(
    id = UUID.randomUUID().toString(),
    solanaCoinId = solanaCoinId,
    mintAddress = mintAddress,
    symbol = symbol,
    name = name,
    decimals = decimals
)

// ===== EVM Token Mappers =====
fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken.toEntity(walletId: String): EVMTokenEntity = EVMTokenEntity(
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
        is com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH -> "NATIVE"
        is com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken -> "USDC"
        is com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken -> "USDT"
        is com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token -> "ERC20"
    },
    externalId = externalId,
    updatedAt = System.currentTimeMillis()
)

fun EVMTokenEntity.toDomain(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken {
    val network = network.toEthereumNetwork()

    return when (tokenType) {
        "NATIVE" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress
        )
        "USDC" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress
        )
        "USDT" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken(
            address = address,
            publicKey = publicKey,
            network = network,
            contractAddress = contractAddress
        )
        else -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token(
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
fun BitcoinBalanceEntity.toDomain(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance(
        address = address,
        satoshis = satoshis,
        btc = btc,
        usdValue = usdValue
    )

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance.toEntity(coinId: String): BitcoinBalanceEntity = BitcoinBalanceEntity(
    id = UUID.randomUUID().toString(),
    coinId = coinId,
    address = address,
    satoshis = satoshis,
    btc = btc,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)

// ===== Solana Balance Mappers =====
fun SolanaBalanceEntity.toDomain(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance(
        address = address,
        lamports = lamports,
        sol = sol,
        usdValue = usdValue
    )

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance.toEntity(coinId: String): SolanaBalanceEntity = SolanaBalanceEntity(
    id = UUID.randomUUID().toString(),
    coinId = coinId,
    address = address,
    lamports = lamports,
    sol = sol,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)
// ===== EVM Balance Mappers =====

fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance.toEntity(walletId: String, tokenEntity: EVMTokenEntity): EVMBalanceEntity = EVMBalanceEntity(
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

fun EVMBalanceEntity.toDomain(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance =
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance(
        externalTokenId = externalTokenId,
        address = address,
        balanceWei = balanceWei,
        balanceDecimal = balanceDecimal,
        usdValue = usdValue
    )

// ===== Network Conversion Helpers =====

// BitcoinNetwork conversion
fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork.toStorageString(): String = when (this) {
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork.Mainnet -> "Mainnet"
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork.Testnet -> "Testnet"
}

fun String.toBitcoinNetwork(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork = when (this) {
    "Mainnet" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork.Mainnet
    "Testnet" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork.Testnet
    else -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork.Testnet
}

// SolanaNetwork conversion
fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork.toStorageString(): String = when (this) {
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork.Mainnet -> "Mainnet"
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork.Devnet -> "Devnet"
}

fun String.toSolanaNetwork(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork = when (this) {
    "Mainnet" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork.Mainnet
    "Devnet" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork.Devnet
    else -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork.Devnet
}

// EthereumNetwork conversion
fun com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork.toStorageString(): String = when (this) {
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork.Mainnet -> "Mainnet"
    _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork.Sepolia -> "Sepolia"
}

fun String.toEthereumNetwork(): com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork = when (this) {
    "Mainnet" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork.Mainnet
    "Sepolia" -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork.Sepolia
    else -> _root_ide_package_.com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork.Sepolia
}