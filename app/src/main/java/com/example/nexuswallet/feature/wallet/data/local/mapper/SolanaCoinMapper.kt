package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import java.util.UUID

fun SolanaCoinEntity.toDomain(splTokens: List<SPLToken>): SolanaCoin =
    SolanaCoin(
        address = address,
        publicKey = publicKey,
        derivationPath = derivationPath,
        network = network,
        splTokens = splTokens
    )

fun SolanaCoin.toEntity(walletId: String): SolanaCoinEntity = SolanaCoinEntity(
    id = "${walletId}_${network.name}_$address",
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = derivationPath,
    network = network
)