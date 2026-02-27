package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.solana.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.coin.Result

interface CreateWalletUseCase {
    suspend operator fun invoke(
        mnemonic: List<String>,
        name: String,
        includeBitcoin: Boolean,
        includeEthereum: Boolean,
        includeSolana: Boolean,
        includeUSDC: Boolean,
        ethereumNetwork: EthereumNetwork,
        bitcoinNetwork: BitcoinNetwork,
        solanaNetwork: SolanaNetwork
    ): Result<Wallet>
}