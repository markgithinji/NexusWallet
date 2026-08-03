package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import java.util.UUID

fun SolanaBalanceEntity.toDomain(): SolanaBalance =
    SolanaBalance(
        address = address,
        lamports = lamports,
        sol = sol,
        usdValue = usdValue
    )

fun SolanaBalance.toEntity(coinId: String): SolanaBalanceEntity = SolanaBalanceEntity(
    id = coinId,
    coinId = coinId,
    address = address,
    lamports = lamports,
    sol = sol,
    usdValue = usdValue,
    updatedAt = System.currentTimeMillis()
)