package com.example.nexuswallet.feature.settings.domain.model

import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.Network

data class RestoreSelection(
    val selectedWallets: Set<String> = emptySet(),
    val selectedNetworks: Map<String, Set<String>> = emptyMap(), // walletId -> set of network names
    val selectedTokens: Map<String, Map<String, Set<EVMTokenType>>> = emptyMap() // walletId -> (networkName -> set of tokens)
)
