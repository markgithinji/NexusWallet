package com.example.nexuswallet.feature.bitcoin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//@Serializable
//sealed class BitcoinNetwork {
//    abstract val name: String
//    abstract val displayName: String
//    abstract val isTestnet: Boolean
//
//    @Serializable
//    @SerialName("BitcoinMainnet")
//    data object Mainnet : BitcoinNetwork() {
//        override val name = "BitcoinMainnet"
//        override val displayName = "Bitcoin"
//        override val isTestnet = false
//    }
//
//    @Serializable
//    @SerialName("BitcoinTestnet")
//    data object Testnet : BitcoinNetwork() {
//        override val name = "BitcoinTestnet"
//        override val displayName = "Bitcoin Testnet"
//        override val isTestnet = true
//    }
//}