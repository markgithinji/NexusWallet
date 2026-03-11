package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet

interface CreateWalletUseCase {
    suspend operator fun invoke(
        mnemonic: List<String>,
        name: String,
        // Bitcoin networks
        includeBitcoinMainnet: Boolean = true,
        includeBitcoinTestnet: Boolean = true,

        // Ethereum networks
        includeEthereumMainnet: Boolean = true,
        includeEthereumSepolia: Boolean = true,

        // Solana networks
        includeSolanaMainnet: Boolean = true,
        includeSolanaDevnet: Boolean = true,

        // Tokens
        includeUSDCMainnet: Boolean = false,
        includeUSDCSepolia: Boolean = false,
        includeUSDTMainnet: Boolean = false
    ): Result<Wallet>
}