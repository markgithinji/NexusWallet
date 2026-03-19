package com.example.nexuswallet.feature.wallet.util

import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork

object ExplorerUrlHelper {
    fun getExplorerUrl(txHash: String, network: Network): String {
        return when (network) {
            is BitcoinNetwork -> {
                when (network) {
                    BitcoinNetwork.Mainnet -> "https://blockstream.info/tx/$txHash"
                    BitcoinNetwork.Testnet -> "https://blockstream.info/testnet/tx/$txHash"
                }
            }

            is EthereumNetwork -> {
                when (network) {
                    EthereumNetwork.Mainnet -> "https://etherscan.io/tx/$txHash"
                    EthereumNetwork.Sepolia -> "https://sepolia.etherscan.io/tx/$txHash"
                }
            }

            is SolanaNetwork -> {
                when (network) {
                    SolanaNetwork.Mainnet -> "https://solscan.io/tx/$txHash"
                    SolanaNetwork.Devnet -> "https://solscan.io/tx/$txHash?cluster=devnet"
                }
            }
        }
    }
}