package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import java.util.UUID

fun EVMBalance.toEntity(walletId: String, tokenEntity: EVMTokenEntity): EVMBalanceEntity =
    EVMBalanceEntity(
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

fun EVMBalanceEntity.toDomain(): EVMBalance =
    EVMBalance(
        externalTokenId = externalTokenId,
        address = address,
        balanceWei = balanceWei,
        balanceDecimal = balanceDecimal,
        usdValue = usdValue
    )