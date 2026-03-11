package com.example.nexuswallet.feature.wallet.ui

import com.example.nexuswallet.feature.core.domain.model.CoinType

object ExplorerUrlHelper {
    fun getExplorerUrl(txHash: String, coinType: CoinType, network: String?): String {
        return when (coinType) {
            CoinType.BITCOIN -> {
                when (network?.lowercase()) {
                    "testnet" -> "https://blockstream.info/testnet/tx/$txHash"
                    else -> "https://blockstream.info/tx/$txHash"
                }
            }
            CoinType.ETHEREUM, CoinType.USDC -> {
                when (network?.lowercase()) {
                    "sepolia" -> "https://sepolia.etherscan.io/tx/$txHash"
                    else -> "https://etherscan.io/tx/$txHash"
                }
            }
            CoinType.SOLANA -> {
                when (network?.lowercase()) {
                    "devnet" -> "https://solscan.io/tx/$txHash?cluster=devnet"
                    else -> "https://solscan.io/tx/$txHash"
                }
            }
        }
    }
}