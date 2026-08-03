package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import java.util.UUID

fun EVMToken.toEntity(walletId: String): EVMTokenEntity = EVMTokenEntity(
    id = "${walletId}_${network.name}_${evmTokenType.name}_$address",
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = "m/44'/60'/0'/0/0",
    network = network,
    contractAddress = contractAddress,
    evmTokenType = evmTokenType,
    updatedAt = System.currentTimeMillis()
)

fun EVMTokenEntity.toDomain(): EVMToken = when (evmTokenType) {
    EVMTokenType.NATIVE -> NativeETH(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
    )

    EVMTokenType.USDC -> USDCToken(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
    )

    EVMTokenType.USDT -> USDTToken(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
    )
}