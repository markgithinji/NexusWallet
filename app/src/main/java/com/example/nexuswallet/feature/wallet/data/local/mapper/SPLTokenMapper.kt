package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.SPLTokenEntity
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import java.util.UUID

fun SPLTokenEntity.toDomain(): SPLToken =
    SPLToken(
        mintAddress = mintAddress,
        symbol = symbol,
        name = name,
        decimals = decimals
    )

fun SPLToken.toEntity(solanaCoinId: String): SPLTokenEntity = SPLTokenEntity(
    id = "${solanaCoinId}_$mintAddress",
    solanaCoinId = solanaCoinId,
    mintAddress = mintAddress,
    symbol = symbol,
    name = name,
    decimals = decimals
)