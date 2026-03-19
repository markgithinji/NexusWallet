package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import java.util.UUID

fun BitcoinCoinEntity.toDomain(): BitcoinCoin =
    BitcoinCoin(
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