package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.util.formatCurrency
import com.example.nexuswallet.feature.core.util.formatPercent
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.wallet.domain.model.AssetDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatBalanceUseCase @Inject constructor() {

    operator fun invoke(
        walletId: String,
        wallet: Wallet,
        balance: WalletBalance?,
        pricePercentages: Map<String, Double>,
        currency: SupportedCurrency
    ): List<AssetDisplayInfo> {
        val assets = mutableListOf<AssetDisplayInfo>()

        // Add Bitcoin assets
        wallet.bitcoinCoins.forEach { coin ->
            val coinBalance = balance?.bitcoinBalances?.get(coin.network)
            val percentage = pricePercentages["bitcoin"]

            assets.add(
                AssetDisplayInfo(
                    id = "btc_${coin.network.name}_${coin.address}",
                    walletId = walletId,
                    coin = coin,
                    name = coin.name,
                    symbol = coin.symbol,
                    network = coin.network,
                    isTestnet = coin.network.isTestnet,
                    balance = coinBalance?.btc ?: "0",
                    balanceFormatted = formatCryptoAmount(coinBalance?.btc ?: "0"),
                    usdValue = coinBalance?.usdValue ?: 0.0,
                    usdValueFormatted = (coinBalance?.usdValue ?: 0.0).formatCurrency(currency),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.formatPercent(),
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
                    coin = coin,
                    name = coin.name,
                    symbol = coin.symbol,
                    network = coin.network,
                    isTestnet = coin.network.isTestnet,
                    balance = coinBalance?.sol ?: "0",
                    balanceFormatted = formatCryptoAmount(coinBalance?.sol ?: "0"),
                    usdValue = coinBalance?.usdValue ?: 0.0,
                    usdValueFormatted = (coinBalance?.usdValue ?: 0.0).formatCurrency(currency),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.formatPercent(),
                    tokenCount = coin.splTokens.size,
                    address = coin.address
                )
            )

            // Add SPL tokens
            coin.splTokens.forEach { token ->
                assets.add(
                    AssetDisplayInfo(
                        id = "spl_${token.mintAddress}",
                        walletId = walletId,
                        coin = coin,
                        name = token.name,
                        symbol = token.symbol,
                        network = coin.network,
                        isTestnet = coin.network.isTestnet,
                        balance = "0",
                        balanceFormatted = "0",
                        usdValue = 0.0,
                        usdValueFormatted = (0.0).formatCurrency(currency),
                        priceChangePercentage = null,
                        priceChangeFormatted = null,
                        address = token.mintAddress
                    )
                )
            }
        }

        // Add EVM assets - using direct filtering instead of map
        wallet.evmTokens.forEach { token ->
            // Find the matching balance using network and tokenType directly
            val tokenBalance = balance?.evmBalances?.find {
                it.network == token.network && it.evmTokenType == token.evmTokenType
            }

            val percentage = when (token) {
                is NativeETH -> pricePercentages["ethereum"]
                is USDCToken -> pricePercentages["usd-coin"]
                is USDTToken -> pricePercentages["tether"]
            }

            assets.add(
                AssetDisplayInfo(
                    id = "${token.network.chainId}_${token.evmTokenType}_${token.address}",
                    walletId = walletId,
                    coin = token,
                    name = token.name,
                    symbol = token.symbol,
                    network = token.network,
                    isTestnet = token.network.isTestnet,
                    balance = tokenBalance?.balanceDecimal ?: "0",
                    balanceFormatted = formatCryptoAmount(tokenBalance?.balanceDecimal ?: "0"),
                    usdValue = tokenBalance?.usdValue ?: 0.0,
                    usdValueFormatted = (tokenBalance?.usdValue
                        ?: 0.0).formatCurrency(currency),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.formatPercent(),
                    address = token.address
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
                    amountDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                        .toPlainString()

                amountDecimal < BigDecimal("0.001") ->
                    amountDecimal.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                        .toPlainString()

                amountDecimal < BigDecimal("1") ->
                    amountDecimal.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                        .toPlainString()

                else ->
                    amountDecimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
                        .toPlainString()
            }
        } catch (e: Exception) {
            amount
        }
    }
}
