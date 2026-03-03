package com.example.nexuswallet.feature.coin

enum class NetworkType(val displayName: String, val apiValue: String) {
    BITCOIN_MAINNET("Bitcoin Mainnet", "mainnet"),
    BITCOIN_TESTNET("Bitcoin Testnet", "testnet"),
    ETHEREUM_MAINNET("Ethereum Mainnet", "mainnet"),
    ETHEREUM_SEPOLIA("Ethereum Sepolia", "sepolia"),
    SOLANA_MAINNET("Solana Mainnet", "mainnet"),
    SOLANA_DEVNET("Solana Devnet", "devnet");

    companion object {
        fun fromCoinTypeAndNetwork(coinType: CoinType, networkString: String): NetworkType? {
            return when (coinType) {
                CoinType.BITCOIN -> when (networkString.lowercase()) {
                    "mainnet" -> BITCOIN_MAINNET
                    "testnet" -> BITCOIN_TESTNET
                    else -> null
                }
                CoinType.ETHEREUM, CoinType.USDC -> when (networkString.lowercase()) {
                    "mainnet" -> ETHEREUM_MAINNET
                    "sepolia" -> ETHEREUM_SEPOLIA
                    else -> null
                }
                CoinType.SOLANA -> when (networkString.lowercase()) {
                    "mainnet" -> SOLANA_MAINNET
                    "devnet" -> SOLANA_DEVNET
                    else -> null
                }
            }
        }
    }
}