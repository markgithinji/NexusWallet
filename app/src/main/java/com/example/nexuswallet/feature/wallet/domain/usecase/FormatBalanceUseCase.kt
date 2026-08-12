package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.util.formatAsCurrency
import com.example.nexuswallet.feature.core.util.formatCryptoAmount
import com.example.nexuswallet.feature.core.util.formatPercent
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.wallet.domain.model.AssetDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatBalanceUseCase @Inject constructor() {

    operator fun invoke(
        walletId: String,
        wallet: Wallet,
        balance: WalletBalance?,
        pricePercentages: Map<String, Double>,
        currency: SupportedCurrency,
        usdToRate: Double = 1.0
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
                    balanceFormatted = (coinBalance?.btc ?: "0").formatCryptoAmount(),
                    usdValue = coinBalance?.usdValue ?: BigDecimal.ZERO,
                    usdValueFormatted = (coinBalance?.usdValue ?: BigDecimal.ZERO).formatAsCurrency(usdToRate, currency),
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
                    balanceFormatted = (coinBalance?.sol ?: "0").formatCryptoAmount(),
                    usdValue = coinBalance?.usdValue ?: BigDecimal.ZERO,
                    usdValueFormatted = (coinBalance?.usdValue ?: BigDecimal.ZERO).formatAsCurrency(usdToRate, currency),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.formatPercent(),
                    tokenCount = coin.splTokens.size,
                    address = coin.address
                )
            )

            // Add SPL tokens
            coin.splTokens.forEach { token ->
                val tokenBalance = balance?.splBalances?.get(token.mintAddress)
                
                assets.add(
                    AssetDisplayInfo(
                        id = "spl_${token.mintAddress}",
                        walletId = walletId,
                        coin = coin,
                        name = token.name,
                        symbol = token.symbol,
                        network = coin.network,
                        isTestnet = coin.network.isTestnet,
                        balance = tokenBalance?.balanceDecimal ?: "0",
                        balanceFormatted = (tokenBalance?.balanceDecimal ?: "0").formatCryptoAmount(),
                        usdValue = tokenBalance?.usdValue ?: BigDecimal.ZERO,
                        usdValueFormatted = (tokenBalance?.usdValue ?: BigDecimal.ZERO).formatAsCurrency(usdToRate, currency),
                        priceChangePercentage = null,
                        priceChangeFormatted = null,
                        address = token.mintAddress
                    )
                )
            }
        }

        // Add EVM assets - using efficient map lookup
        wallet.evmTokens.forEach { token ->
            val lookupKey = "${token.network.chainId}_${token.contractAddress}"
            val tokenBalance = balance?.evmBalances?.get(lookupKey)

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
                    balanceFormatted = (tokenBalance?.balanceDecimal ?: "0").formatCryptoAmount(),
                    usdValue = tokenBalance?.usdValue ?: BigDecimal.ZERO,
                    usdValueFormatted = (tokenBalance?.usdValue
                        ?: BigDecimal.ZERO).formatAsCurrency(usdToRate, currency),
                    priceChangePercentage = percentage,
                    priceChangeFormatted = percentage?.formatPercent(),
                    address = token.address
                )
            )
        }

        return assets
    }
}
