package com.example.nexuswallet.feature.core.domain.model

enum class NetworkType(val displayName: String, val apiValue: String) {
    BITCOIN_MAINNET("Bitcoin Mainnet", "mainnet"),
    BITCOIN_TESTNET("Bitcoin Testnet", "testnet"),
    ETHEREUM_MAINNET("Ethereum Mainnet", "mainnet"),
    ETHEREUM_SEPOLIA("Ethereum Sepolia", "sepolia"),
    SOLANA_MAINNET("Solana Mainnet", "mainnet"),
    SOLANA_DEVNET("Solana Devnet", "devnet");
}