package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.domain.model.ERC20Token
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import java.util.UUID

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

fun EthereumNetwork.toStorageString(): String = when (this) {
    EthereumNetwork.Mainnet -> "Mainnet"
    EthereumNetwork.Sepolia -> "Sepolia"
}

fun String.toEthereumNetwork(): EthereumNetwork = when (this) {
    "Mainnet" -> EthereumNetwork.Mainnet
    "Sepolia" -> EthereumNetwork.Sepolia
    else -> EthereumNetwork.Sepolia
}