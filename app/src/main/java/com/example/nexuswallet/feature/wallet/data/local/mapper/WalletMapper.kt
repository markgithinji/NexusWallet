package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.Wallet

fun WalletEntity.toDomain(
    bitcoinCoins: List<BitcoinCoin>,
    solanaCoins: List<SolanaCoin>,
    evmTokens: List<EVMToken>
): Wallet =
    Wallet(
        id = id,
        name = name,
        mnemonicHash = mnemonicHash,
        createdAt = createdAt,
        isBackedUp = isBackedUp,
        bitcoinCoins = bitcoinCoins,
        solanaCoins = solanaCoins,
        evmTokens = evmTokens
    )

fun Wallet.toEntity(): WalletEntity = WalletEntity(
    id = id,
    name = name,
    mnemonicHash = mnemonicHash,
    createdAt = createdAt,
    isBackedUp = isBackedUp
)