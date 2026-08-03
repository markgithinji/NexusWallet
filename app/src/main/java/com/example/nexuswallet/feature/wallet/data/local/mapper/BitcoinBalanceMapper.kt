package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import java.util.UUID

fun BitcoinBalanceEntity.toDomain(): BitcoinBalance =
    BitcoinBalance(
        address = address,
        satoshis = satoshis,
        btc = btc,
        usdValue = usdValue
    )

fun BitcoinBalance.toEntity(coinId: String): BitcoinBalanceEntity = BitcoinBalanceEntity(
    id = coinId,
    coinId = coinId,
    address = address,
    satoshis = satoshis,
    btc = btc,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)