package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance

fun EVMBalance.toEntity(walletId: String): EVMBalanceEntity =
    EVMBalanceEntity(
        id = "${walletId}_${network.name}_${evmTokenType.name}",
        walletId = walletId,
        evmTokenType = evmTokenType,
        network = network,
        address = address,
        contractAddress = contractAddress,
        balanceWei = balanceWei,
        balanceDecimal = balanceDecimal,
        usdValue = usdValue,
        updatedAt = System.currentTimeMillis()
    )

fun EVMBalanceEntity.toDomain(): EVMBalance =
    EVMBalance(
        evmTokenType = evmTokenType,
        network = network,
        address = address,
        contractAddress = contractAddress,
        balanceWei = balanceWei,
        balanceDecimal = balanceDecimal,
        usdValue = usdValue
    )
