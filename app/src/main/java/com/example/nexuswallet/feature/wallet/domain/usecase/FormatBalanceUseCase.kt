package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.wallet.domain.model.AssetDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.AssetType
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatBalanceUseCase @Inject constructor() {

    private val usdFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    operator fun invoke(
        walletId: String,
        wallet: Wallet,
        balance: WalletBalance?,
        pricePercentages: Map<String, Double>
    ): List<AssetDisplayInfo> {
        val balanceMap = balance?.evmBalances?.associateBy { it.externalTokenId } ?: emptyMap()
        val assets = mutableListOf<AssetDisplayInfo>()

        // Add Bitcoin assets
        wallet.bitcoinCoins.forEach { coin ->
            val coinBalance = balance?.bitcoinBalances?.get(coin.network)
            val percentage = pricePercentages["bitcoin"]

            assets.add(
                AssetDisplayInfo(
                    id = "btc_${coin.network.name}_${coin.address}",
                    walletId = walletId,
                    name = "Bitcoin",
                    symbol = "BTC",
                    network = coin.network,
                    networkDisplayName = coin.network.displayName,
                    isTestnet = coin.network.isTestnet,
                    balance = coinBalance?.btc ?: "0",
                    balanceFormatted = formatCryptoAmount(coinBalance?.btc ?: "0"),
                    usdValue = coinBalance?.usdValue ?: 0.0,
                    usdValueFormatted = usdFormatter.format(coinBalance?.usdValue ?: 0.0),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.let { formatPriceChange(it) },
                    assetType = AssetType.BITCOIN,
                    address = coin.address
                )
            )
        }

        // Add Solana assets
        wallet.solanaCoins.forEach { coin ->
            val coinBalance = balance?.solanaBalances?.get(coin.network)
            val percentage = pricePercentages["solana"]

            assets.add(
                AssetDisplayInfo(
                    id = "sol_${coin.network.name}_${coin.address}",
                    walletId = walletId,
                    name = "Solana",
                    symbol = "SOL",
                    network = coin.network,
                    networkDisplayName = coin.network.displayName,
                    isTestnet = coin.network.isTestnet,
                    balance = coinBalance?.sol ?: "0",
                    balanceFormatted = formatCryptoAmount(coinBalance?.sol ?: "0"),
                    usdValue = coinBalance?.usdValue ?: 0.0,
                    usdValueFormatted = usdFormatter.format(coinBalance?.usdValue ?: 0.0),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.let { formatPriceChange(it) },
                    tokenCount = coin.splTokens.size,
                    assetType = AssetType.SOLANA,
                    address = coin.address
                )
            )

            // Add SPL tokens
            coin.splTokens.forEach { token ->
                assets.add(
                    AssetDisplayInfo(
                        id = "spl_${token.mintAddress}",
                        walletId = walletId,
                        name = token.name,
                        symbol = token.symbol,
                        network = coin.network,
                        networkDisplayName = coin.network.displayName,
                        isTestnet = coin.network.isTestnet,
                        balance = "0", // TODO: Add SPL balance
                        balanceFormatted = "0",
                        usdValue = 0.0,
                        usdValueFormatted = "$0.00",
                        priceChangePercentage = null,
                        priceChangeFormatted = null,
                        assetType = AssetType.SPL,
                        address = token.mintAddress,
                        externalId = token.mintAddress
                    )
                )
            }
        }

        // Add EVM assets
        wallet.evmTokens.forEach { token ->
            val tokenBalance = balanceMap[token.externalId]
            val percentage = when (token) {
                is NativeETH -> pricePercentages["ethereum"]
                is USDCToken -> pricePercentages["usd-coin"]
                is USDTToken -> pricePercentages["tether"]
                else -> null
            }

            val assetType = when (token) {
                is NativeETH -> AssetType.ETHEREUM
                is USDCToken -> AssetType.USDC
                is USDTToken -> AssetType.USDT
                else -> AssetType.ERC20
            }

            assets.add(
                AssetDisplayInfo(
                    id = token.externalId,
                    walletId = walletId,
                    name = token.name,
                    symbol = token.symbol,
                    network = token.network,
                    networkDisplayName = token.network.displayName,
                    isTestnet = token.network.isTestnet,
                    balance = tokenBalance?.balanceDecimal ?: "0",
                    balanceFormatted = formatCryptoAmount(tokenBalance?.balanceDecimal ?: "0"),
                    usdValue = tokenBalance?.usdValue ?: 0.0,
                    usdValueFormatted = usdFormatter.format(tokenBalance?.usdValue ?: 0.0),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.let { formatPriceChange(it) },
                    assetType = assetType,
                    address = token.address,
                    externalId = token.externalId
                )
            )
        }

        return assets
    }

    private fun formatCryptoAmount(amount: String): String {
        return try {
            val amountDecimal = amount.toBigDecimal()
            when {
                amountDecimal < BigDecimal("0.000001") ->
                    amountDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                amountDecimal < BigDecimal("0.001") ->
                    amountDecimal.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                amountDecimal < BigDecimal("1") ->
                    amountDecimal.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                else ->
                    amountDecimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            }
        } catch (e: Exception) {
            amount
        }
    }

    private fun formatPriceChange(percentage: Double): String {
        val sign = if (percentage >= 0) "+" else ""
        return "$sign${String.format("%.2f", percentage)}%"
    }
}