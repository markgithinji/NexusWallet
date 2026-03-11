package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.toBitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.local.toStorageString
import com.example.nexuswallet.feature.wallet.domain.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import java.util.UUID

fun BitcoinCoinEntity.toDomain(): BitcoinCoin =
    BitcoinCoin(
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

fun BitcoinNetwork.toStorageString(): String = when (this) {
    BitcoinNetwork.Mainnet -> "Mainnet"
    BitcoinNetwork.Testnet -> "Testnet"
}

fun String.toBitcoinNetwork(): BitcoinNetwork = when (this) {
    "Mainnet" -> BitcoinNetwork.Mainnet
    "Testnet" -> BitcoinNetwork.Testnet
    else -> BitcoinNetwork.Testnet
}
