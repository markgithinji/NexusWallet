package com.example.nexuswallet.feature.wallet.data.local.mapper

import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.domain.model.ERC20Token
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import java.util.UUID

fun EVMToken.toEntity(walletId: String): EVMTokenEntity = EVMTokenEntity(
    id = UUID.randomUUID().toString(),
    walletId = walletId,
    address = address,
    publicKey = publicKey,
    derivationPath = "m/44'/60'/0'/0/0",
    network = network,
    contractAddress = contractAddress,
    symbol = symbol,
    name = name,
    decimals = decimals,
    tokenType = when (this) {
        is NativeETH -> TokenType.NATIVE
        is USDCToken -> TokenType.USDC
        is USDTToken -> TokenType.USDT
        is ERC20Token -> TokenType.ERC20
    },
    externalId = externalId,
    updatedAt = System.currentTimeMillis()
)

fun EVMTokenEntity.toDomain(): EVMToken = when (tokenType) {
    TokenType.NATIVE -> NativeETH(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
    )
    TokenType.USDC -> USDCToken(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
    )
    TokenType.USDT -> USDTToken(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
    )
    TokenType.ERC20 -> ERC20Token(
        address = address,
        publicKey = publicKey,
        network = network,
        contractAddress = contractAddress,
        symbol = symbol,
        name = name,
        decimals = decimals,
    )
}